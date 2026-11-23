package org.fossify.gallery.engine.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TILE_SIZE = 512

private data class DecodedTile(
    val imageBitmap: ImageBitmap,
    val rawRect: Rect,
    val byteCount: Int
)

@Composable
fun NativeTiledImageViewer(
    baseBitmap: ImageBitmap,
    imageWidth: Int,
    imageHeight: Int,
    orientationDegrees: Int,
    imageUri: Uri,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    onEdgeSwipeHandoff: (Boolean) -> Unit = {},
    onVerticalDismissDrag: (Float) -> Unit = {},
    onVerticalDismissEnd: (Float) -> Unit = {},
    onSwipeUpDetails: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(imageUri) { mutableFloatStateOf(1f) }
    var offset by remember(imageUri) { mutableStateOf(Offset.Zero) }

    var regionDecoder by remember(imageUri) { mutableStateOf<BitmapRegionDecoder?>(null) }
    val tileCache = remember(imageUri) {
        val maxBytes = (Runtime.getRuntime().maxMemory() * 0.08).toInt().coerceAtLeast(8 * 1024 * 1024)
        object : LruCache<String, DecodedTile>(maxBytes) {
            override fun sizeOf(key: String, value: DecodedTile): Int = value.byteCount
        }
    }
    val visibleTiles = remember(imageUri) { mutableStateMapOf<String, DecodedTile>() }
    var tileJob by remember(imageUri) { mutableStateOf<Job?>(null) }

    val normalizedOrientation = ((orientationDegrees % 360) + 360) % 360

    DisposableEffect(imageUri) {
        onEdgeSwipeHandoff(true)
        val openJob = scope.launch(Dispatchers.IO) {
            try {
                val decoder = context.contentResolver.openFileDescriptor(imageUri, "r")?.use { pfd ->
                    BitmapRegionDecoder.newInstance(pfd.fileDescriptor, false)
                }
                if (isActive && decoder != null) {
                    withContext(Dispatchers.Main) {
                        regionDecoder = decoder
                    }
                } else {
                    decoder?.recycle()
                }
            } catch (_: Exception) {
            }
        }

        onDispose {
            openJob.cancel()
            tileJob?.cancel()
            val d = regionDecoder
            regionDecoder = null
            d?.recycle()
            tileCache.evictAll()
            visibleTiles.clear()
        }
    }

    LaunchedEffect(scale, offset, regionDecoder, viewportSize) {
        val decoder = regionDecoder ?: return@LaunchedEffect
        if (scale <= 1.5f || viewportSize.width <= 0 || viewportSize.height <= 0) {
            tileJob?.cancel()
            visibleTiles.clear()
            return@LaunchedEffect
        }

        tileJob?.cancel()
        tileJob = scope.launch(Dispatchers.IO) {
            delay(32)
            if (!isActive) return@launch

            val rawW = decoder.width
            val rawH = decoder.height
            if (rawW <= 0 || rawH <= 0) return@launch

            val rawVisible = computeVisibleRawRect(
                viewportWidth = viewportSize.width.toFloat(),
                viewportHeight = viewportSize.height.toFloat(),
                displayWidth = imageWidth.toFloat(),
                displayHeight = imageHeight.toFloat(),
                rawWidth = rawW,
                rawHeight = rawH,
                orientation = normalizedOrientation,
                scale = scale,
                offset = offset
            )

            val sampleSize = when {
                scale > 4f -> 1
                scale > 2.2f -> 2
                else -> 4
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val startCol = floor(rawVisible.left.toFloat() / TILE_SIZE).toInt().coerceAtLeast(0)
            val endCol = ceil(rawVisible.right.toFloat() / TILE_SIZE).toInt()
                .coerceAtMost((rawW + TILE_SIZE - 1) / TILE_SIZE)
            val startRow = floor(rawVisible.top.toFloat() / TILE_SIZE).toInt().coerceAtLeast(0)
            val endRow = ceil(rawVisible.bottom.toFloat() / TILE_SIZE).toInt()
                .coerceAtMost((rawH + TILE_SIZE - 1) / TILE_SIZE)

            val neededKeys = HashSet<String>()
            val decodedBatch = LinkedHashMap<String, DecodedTile>()

            for (row in startRow until endRow) {
                for (col in startCol until endCol) {
                    if (!isActive) return@launch
                    val key = "${col}_${row}_$sampleSize"
                    neededKeys.add(key)

                    var tile = tileCache.get(key)
                    if (tile == null) {
                        val rect = Rect(
                            col * TILE_SIZE,
                            row * TILE_SIZE,
                            min((col + 1) * TILE_SIZE, rawW),
                            min((row + 1) * TILE_SIZE, rawH)
                        )
                        val bmp = try {
                            synchronized(decoder) {
                                if (!decoder.isRecycled) decoder.decodeRegion(rect, opts) else null
                            }
                        } catch (_: Exception) {
                            null
                        }
                        if (bmp != null) {
                            tile = DecodedTile(bmp.asImageBitmap(), rect, bmp.allocationByteCount)
                            tileCache.put(key, tile)
                        }
                    }
                    if (tile != null) {
                        decodedBatch[key] = tile
                    }
                }
            }

            if (isActive) {
                withContext(Dispatchers.Main) {
                    val stale = visibleTiles.keys.filter { it !in neededKeys }
                    stale.forEach { visibleTiles.remove(it) }
                    visibleTiles.putAll(decodedBatch)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .pointerInput(imageUri) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                            onEdgeSwipeHandoff(true)
                        } else {
                            val newScale = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val diff = tapOffset - center
                            val rawOffset = -diff * (newScale - 1f)
                            scale = newScale
                            offset = clampOffset(rawOffset, newScale, size.width.toFloat(), size.height.toFloat())
                            onEdgeSwipeHandoff(false)
                        }
                    }
                )
            }
            .pointerInput(imageUri) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var verticalDismissActive = false
                    var swipeUpTriggered = false
                    var totalVerticalDrag = 0f
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var lockedHorizontalForPager = false

                    do {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.count { it.pressed }

                        if (activePointers >= 2) {
                            verticalDismissActive = false
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                val oldScale = scale
                                val newScale = (oldScale * zoomChange).coerceIn(1f, 8f)
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val focal = centroid - center
                                val newOffset = (offset - focal) * (newScale / oldScale) + focal + panChange
                                scale = newScale
                                offset = clampOffset(newOffset, newScale, size.width.toFloat(), size.height.toFloat())
                                onEdgeSwipeHandoff(newScale <= 1.02f)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else if (activePointers == 1) {
                            val pan = event.calculatePan()
                            if (scale > 1.02f) {
                                val maxPanX = max(0f, (size.width * scale - size.width) / 2f)
                                val atLeftEdge = offset.x >= maxPanX - 1f && pan.x > 0f
                                val atRightEdge = offset.x <= -maxPanX + 1f && pan.x < 0f
                                if ((atLeftEdge || atRightEdge) && abs(pan.x) > abs(pan.y)) {
                                    onEdgeSwipeHandoff(true)
                                } else {
                                    onEdgeSwipeHandoff(false)
                                    offset = clampOffset(
                                        offset + pan,
                                        scale,
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } else {
                                totalDragX += pan.x
                                totalDragY += pan.y

                                if (!verticalDismissActive && !lockedHorizontalForPager && !swipeUpTriggered) {
                                    val slop = viewConfiguration.touchSlop
                                    if (abs(totalDragX) > slop && abs(totalDragX) > abs(totalDragY)) {
                                        lockedHorizontalForPager = true
                                    } else if (totalDragY > slop && abs(totalDragY) > abs(totalDragX) * 1.15f) {
                                        verticalDismissActive = true
                                    } else if (totalDragY < -slop * 3f && abs(totalDragY) > abs(totalDragX) * 1.25f) {
                                        swipeUpTriggered = true
                                        onSwipeUpDetails()
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }

                                if (verticalDismissActive) {
                                    totalVerticalDrag += pan.y
                                    onVerticalDismissDrag(totalVerticalDrag)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (verticalDismissActive) {
                        onVerticalDismissEnd(totalVerticalDrag)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            if (canvasW <= 0f || canvasH <= 0f || imageWidth <= 0 || imageHeight <= 0) return@Canvas

            val baseAspect = imageWidth.toFloat() / imageHeight.toFloat()
            val canvasAspect = canvasW / canvasH
            val fitW: Float
            val fitH: Float
            if (baseAspect > canvasAspect) {
                fitW = canvasW
                fitH = canvasW / baseAspect
            } else {
                fitH = canvasH
                fitW = canvasH * baseAspect
            }
            val fitLeft = (canvasW - fitW) / 2f
            val fitTop = (canvasH - fitH) / 2f

            translate(left = offset.x, top = offset.y) {
                scale(scale = scale, pivot = center) {
                    drawImage(
                        image = baseBitmap,
                        dstOffset = IntOffset(fitLeft.roundToInt(), fitTop.roundToInt()),
                        dstSize = IntSize(fitW.roundToInt(), fitH.roundToInt())
                    )

                    val decoder = regionDecoder
                    if (scale > 1.5f && decoder != null && visibleTiles.isNotEmpty()) {
                        val rawW = decoder.width.toFloat()
                        val rawH = decoder.height.toFloat()
                        if (rawW > 0f && rawH > 0f) {
                            val isQuarterTurn = normalizedOrientation == 90 || normalizedOrientation == 270
                            val fitScale = if (isQuarterTurn) fitW / rawH else fitW / rawW

                            translate(left = canvasW / 2f, top = canvasH / 2f) {
                                if (normalizedOrientation != 0) {
                                    rotate(degrees = normalizedOrientation.toFloat(), pivot = Offset.Zero) {
                                        scale(scale = fitScale, pivot = Offset.Zero) {
                                            translate(left = -rawW / 2f, top = -rawH / 2f) {
                                                for ((_, tile) in visibleTiles) {
                                                    drawImage(
                                                        image = tile.imageBitmap,
                                                        dstOffset = IntOffset(tile.rawRect.left, tile.rawRect.top),
                                                        dstSize = IntSize(tile.rawRect.width(), tile.rawRect.height())
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    scale(scale = fitScale, pivot = Offset.Zero) {
                                        translate(left = -rawW / 2f, top = -rawH / 2f) {
                                            for ((_, tile) in visibleTiles) {
                                                drawImage(
                                                    image = tile.imageBitmap,
                                                    dstOffset = IntOffset(tile.rawRect.left, tile.rawRect.top),
                                                    dstSize = IntSize(tile.rawRect.width(), tile.rawRect.height())
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun clampOffset(raw: Offset, scale: Float, width: Float, height: Float): Offset {
    val maxX = max(0f, (width * scale - width) / 2f)
    val maxY = max(0f, (height * scale - height) / 2f)
    return Offset(
        x = raw.x.coerceIn(-maxX, maxX),
        y = raw.y.coerceIn(-maxY, maxY)
    )
}

private fun computeVisibleRawRect(
    viewportWidth: Float,
    viewportHeight: Float,
    displayWidth: Float,
    displayHeight: Float,
    rawWidth: Int,
    rawHeight: Int,
    orientation: Int,
    scale: Float,
    offset: Offset
): Rect {
    val baseAspect = displayWidth / displayHeight
    val canvasAspect = viewportWidth / viewportHeight
    val fitW = if (baseAspect > canvasAspect) viewportWidth else viewportHeight * baseAspect
    val isQuarterTurn = orientation == 90 || orientation == 270
    val fitScale = if (isQuarterTurn) fitW / rawHeight.toFloat() else fitW / rawWidth.toFloat()

    val cx = viewportWidth / 2f
    val cy = viewportHeight / 2f
    val corners = arrayOf(
        Offset(0f, 0f),
        Offset(viewportWidth, 0f),
        Offset(0f, viewportHeight),
        Offset(viewportWidth, viewportHeight)
    )

    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY

    for (pt in corners) {
        val vx = (pt.x - offset.x - cx) / scale
        val vy = (pt.y - offset.y - cy) / scale
        val (rx, ry) = when (orientation) {
            90 -> Pair(vy, -vx)
            180 -> Pair(-vx, -vy)
            270 -> Pair(-vy, vx)
            else -> Pair(vx, vy)
        }
        val rawX = rx / fitScale + rawWidth / 2f
        val rawY = ry / fitScale + rawHeight / 2f
        if (rawX < minX) minX = rawX
        if (rawY < minY) minY = rawY
        if (rawX > maxX) maxX = rawX
        if (rawY > maxY) maxY = rawY
    }

    return Rect(
        floor(minX).toInt().coerceIn(0, rawWidth),
        floor(minY).toInt().coerceIn(0, rawHeight),
        ceil(maxX).toInt().coerceIn(0, rawWidth),
        ceil(maxY).toInt().coerceIn(0, rawHeight)
    )
}
