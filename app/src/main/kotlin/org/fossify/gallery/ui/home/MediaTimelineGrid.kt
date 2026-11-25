package org.fossify.gallery.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.fossify.gallery.engine.image.NativeThumbnailLoader
import org.fossify.gallery.engine.image.rememberThumbnail
import org.fossify.gallery.engine.vault.VaultManager
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.theme.LuminaMotion
import org.fossify.gallery.ui.theme.rememberLuminaPillTokens
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val SelectedTileShape = RoundedCornerShape(14.dp)

fun LazyGridState.findItemBoundsInRoot(gridRootOffset: Offset, mediaId: Long): Rect? {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == mediaId } ?: return null
    val left = gridRootOffset.x + item.offset.x.toFloat()
    val top = gridRootOffset.y + (item.offset.y - info.viewportStartOffset).toFloat()
    return Rect(
        left = left,
        top = top,
        right = left + item.size.width.toFloat(),
        bottom = top + item.size.height.toFloat()
    )
}

@Composable
fun MediaTimelineGrid(
    media: List<Medium>,
    sections: List<TimelineSection> = emptyList(),
    showSectionHeaders: Boolean = false,
    indexToDatePill: List<String>,
    columnCount: Int,
    gridState: LazyGridState,
    selectedIds: Set<Long>,
    isMultiSelectMode: Boolean,
    hiddenMediaId: Long?,
    onGridRootOffsetChange: (Offset) -> Unit,
    onColumnCountChange: (Int) -> Unit,
    onMediaClick: (Medium) -> Unit,
    onMediaToggleSelect: (Medium) -> Unit,
    onSelectionChange: (Set<Long>) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val clampedColumns = columnCount.coerceIn(2, 5)

    val latestMedia by rememberUpdatedState(media)
    val latestSelectedIds by rememberUpdatedState(selectedIds)
    val latestColumns by rememberUpdatedState(clampedColumns)
    val latestOnColumnsChange by rememberUpdatedState(onColumnCountChange)
    val latestOnSelectionChange by rememberUpdatedState(onSelectionChange)

    val idToFlatIndex = remember(media) {
        HashMap<Long, Int>(media.size).apply {
            media.forEachIndexed { i, m -> put(m.id, i) }
        }
    }
    val latestIdToFlatIndex by rememberUpdatedState(idToFlatIndex)

    // Drag-to-select state (single grid-level detector -> zero per-tile gesture overhead)
    var gridBoxSize by remember { mutableStateOf(IntSize.Zero) }
    var isDragSelecting by remember { mutableStateOf(false) }
    var dragPointerY by remember { mutableFloatStateOf(0f) }
    var dragPointerX by remember { mutableFloatStateOf(0f) }
    var dragAnchorIndex by remember { mutableIntStateOf(-1) }
    var dragLastIndex by remember { mutableIntStateOf(-1) }
    var dragBaseSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var dragAddsSelection by remember { mutableStateOf(true) }

    fun findMediaIndexAt(pos: Offset): Int {
        val info = gridState.layoutInfo
        for (item in info.visibleItemsInfo) {
            val id = item.key as? Long ?: continue
            val left = item.offset.x.toFloat()
            val top = (item.offset.y - info.viewportStartOffset).toFloat()
            if (pos.x in left..(left + item.size.width) && pos.y in top..(top + item.size.height)) {
                return latestIdToFlatIndex[id] ?: -1
            }
        }
        return -1
    }

    fun applyDragRange(currentIndex: Int) {
        val anchor = dragAnchorIndex
        val list = latestMedia
        if (anchor !in list.indices || currentIndex !in list.indices) return
        if (currentIndex == dragLastIndex) return
        dragLastIndex = currentIndex

        val start = min(anchor, currentIndex)
        val end = max(anchor, currentIndex)
        val next = dragBaseSelection.toMutableSet()
        for (i in start..end) {
            val id = list[i].id
            if (dragAddsSelection) next.add(id) else next.remove(id)
        }
        latestOnSelectionChange(next)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // Edge auto-scroll while drag-selecting near top or bottom 14% of viewport
    val maxAutoScrollPxPerFrame = with(density) { 18.dp.toPx() }
    LaunchedEffect(isDragSelecting) {
        if (!isDragSelecting) return@LaunchedEffect
        while (isActive && isDragSelecting) {
            withFrameNanos { }
            val h = gridBoxSize.height.toFloat()
            if (h <= 0f) continue
            val topZone = h * 0.14f
            val bottomZone = h * 0.86f
            val delta = when {
                dragPointerY < topZone -> {
                    val ratio = ((topZone - dragPointerY) / topZone).coerceIn(0f, 1f)
                    -maxAutoScrollPxPerFrame * ratio
                }
                dragPointerY > bottomZone -> {
                    val ratio = ((dragPointerY - bottomZone) / (h - bottomZone)).coerceIn(0f, 1f)
                    maxAutoScrollPxPerFrame * ratio
                }
                else -> 0f
            }
            if (delta != 0f) {
                gridState.scrollBy(delta)
                val hit = findMediaIndexAt(Offset(dragPointerX, dragPointerY))
                if (hit != -1) applyDragRange(hit)
            }
        }
    }

    // 2-row lookahead thumbnail prefetching ahead of viewport
    LaunchedEffect(gridState, media, clampedColumns) {
        if (media.isEmpty()) return@LaunchedEffect
        val resolver = context.contentResolver
        snapshotFlow {
            val visible = gridState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) -1 to -1 else visible.first().index to visible.last().index
        }
            .distinctUntilChanged()
            .collect { (firstVis, lastVis) ->
                if (lastVis < 0) return@collect
                val aheadStart = (lastVis + 1).coerceAtMost(media.size)
                val aheadEnd = (lastVis + 1 + clampedColumns * 2).coerceAtMost(media.size)
                val behindStart = (firstVis - clampedColumns).coerceAtLeast(0)
                val behindEnd = firstVis.coerceAtLeast(0)

                val toWarm = ArrayList<Medium>(clampedColumns * 3)
                for (i in aheadStart until aheadEnd) toWarm.add(media[i])
                for (i in behindStart until behindEnd) toWarm.add(media[i])

                if (toWarm.isNotEmpty()) {
                    NativeThumbnailLoader.prefetch(resolver, toWarm)
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { gridBoxSize = it }
            // 2-finger pinch-to-zoom column count
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var zoomAccumulator = 1f
                    var wasMultiTouch = false

                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val activePointers = event.changes.count { it.pressed }
                        if (activePointers >= 2) {
                            wasMultiTouch = true
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                zoomAccumulator *= zoom
                                event.changes.forEach { it.consume() }
                                if (zoomAccumulator >= 1.24f) {
                                    val next = (latestColumns - 1).coerceIn(2, 5)
                                    if (next != latestColumns) {
                                        latestOnColumnsChange(next)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    zoomAccumulator = 1f
                                } else if (zoomAccumulator <= 0.78f) {
                                    val next = (latestColumns + 1).coerceIn(2, 5)
                                    if (next != latestColumns) {
                                        latestOnColumnsChange(next)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    zoomAccumulator = 1f
                                }
                            }
                        } else if (wasMultiTouch) {
                            zoomAccumulator = 1f
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            // Long-press + drag contiguous range selection with edge auto-scroll
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startPos ->
                        val hit = findMediaIndexAt(startPos)
                        if (hit != -1) {
                            val list = latestMedia
                            val hitId = list[hit].id
                            val base = latestSelectedIds
                            val addMode = hitId !in base

                            dragAnchorIndex = hit
                            dragLastIndex = hit
                            dragBaseSelection = base
                            dragAddsSelection = addMode
                            dragPointerX = startPos.x
                            dragPointerY = startPos.y
                            isDragSelecting = true

                            val updated = if (addMode) base + hitId else base - hitId
                            latestOnSelectionChange(updated)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, _ ->
                        if (!isDragSelecting) return@detectDragGesturesAfterLongPress
                        change.consume()
                        dragPointerX = change.position.x
                        dragPointerY = change.position.y
                        val hit = findMediaIndexAt(change.position)
                        if (hit != -1) applyDragRange(hit)
                    },
                    onDragEnd = {
                        isDragSelecting = false
                        dragAnchorIndex = -1
                        dragLastIndex = -1
                    },
                    onDragCancel = {
                        isDragSelecting = false
                        dragAnchorIndex = -1
                        dragLastIndex = -1
                    }
                )
            }
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(clampedColumns),
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    onGridRootOffsetChange(coords.positionInRoot())
                },
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (showSectionHeaders && sections.isNotEmpty()) {
                sections.forEach { section ->
                    item(
                        key = "group_hdr_${section.title}",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "group_header"
                    ) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp)
                        )
                    }

                    items(
                        items = section.items,
                        key = { it.id },
                        contentType = { "media_item" }
                    ) { medium ->
                        val isSelected = isMultiSelectMode && medium.id in selectedIds
                        TimelineMediaItem(
                            medium = medium,
                            isSelected = isSelected,
                            isMultiSelectMode = isMultiSelectMode,
                            isHidden = medium.id == hiddenMediaId,
                            isLockedItem = VaultManager.isVaultMedium(medium),
                            onClick = {
                                if (isMultiSelectMode) onMediaToggleSelect(medium) else onMediaClick(medium)
                            }
                        )
                    }
                }
            } else {
                items(
                    items = media,
                    key = { it.id },
                    contentType = { "media_item" }
                ) { medium ->
                    val isSelected = isMultiSelectMode && medium.id in selectedIds
                    TimelineMediaItem(
                        medium = medium,
                        isSelected = isSelected,
                        isMultiSelectMode = isMultiSelectMode,
                        isHidden = medium.id == hiddenMediaId,
                        isLockedItem = VaultManager.isVaultMedium(medium),
                        onClick = {
                            if (isMultiSelectMode) onMediaToggleSelect(medium) else onMediaClick(medium)
                        }
                    )
                }
            }
        }

        if (media.size >= 36 && !isDragSelecting) {
            FastScrollOverlay(
                gridState = gridState,
                totalItems = indexToDatePill.size.coerceAtLeast(media.size),
                indexToDatePill = indexToDatePill,
                topInset = contentPadding.calculateTopPadding() + 8.dp,
                bottomInset = contentPadding.calculateBottomPadding() + 8.dp,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun FastScrollOverlay(
    gridState: LazyGridState,
    totalItems: Int,
    indexToDatePill: List<String>,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val tokens = rememberLuminaPillTokens()

    var trackHeightPx by remember { mutableIntStateOf(1) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var isVisible by remember { mutableStateOf(false) }
    var lastHapticDate by remember { mutableStateOf("") }

    val scrollFraction by remember(gridState, totalItems) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val visibleCount = info.visibleItemsInfo.size.coerceAtLeast(1)
            val maxScrollIndex = (totalItems - visibleCount).coerceAtLeast(1)
            (gridState.firstVisibleItemIndex.toFloat() / maxScrollIndex.toFloat()).coerceIn(0f, 1f)
        }
    }

    val isScrolling by remember(gridState) {
        derivedStateOf { gridState.isScrollInProgress }
    }

    LaunchedEffect(isScrolling, isDragging) {
        if (isScrolling || isDragging) {
            isVisible = true
        } else {
            delay(1100L)
            isVisible = false
        }
    }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (isVisible) 120 else 260),
        label = "fast_scroll_alpha"
    )

    val activeFraction = if (isDragging) dragFraction else scrollFraction
    val thumbHeightPx = with(density) { 46.dp.roundToPx() }
    val usableTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1)
    val thumbOffsetY = (activeFraction * usableTrackPx).roundToInt().coerceIn(0, usableTrackPx)

    val activeDateLabel = remember(activeFraction, indexToDatePill) {
        if (indexToDatePill.isEmpty()) "" else {
            val idx = (activeFraction * indexToDatePill.lastIndex).roundToInt()
                .coerceIn(0, indexToDatePill.lastIndex)
            indexToDatePill[idx]
        }
    }

    if (overlayAlpha <= 0.01f && !isDragging) return

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(top = topInset.coerceAtLeast(0.dp), bottom = bottomInset.coerceAtLeast(0.dp))
            .width(180.dp)
            .onSizeChanged { trackHeightPx = it.height.coerceAtLeast(1) }
            .graphicsLayer { alpha = overlayAlpha },
        contentAlignment = Alignment.TopEnd
    ) {
        Row(
            modifier = Modifier
                .offset { IntOffset(0, thumbOffsetY) }
                .padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isDragging && activeDateLabel.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = tokens.containerColor,
                    border = tokens.border,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = activeDateLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.contentColor,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = if (isDragging) MaterialTheme.colorScheme.primaryContainer else tokens.containerColor,
                border = tokens.border,
                shadowElevation = if (isDragging) 8.dp else 4.dp,
                modifier = Modifier
                    .size(width = 28.dp, height = 46.dp)
                    .pointerInput(totalItems, usableTrackPx) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragFraction = scrollFraction
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val nextFrac = (dragFraction + dragAmount / usableTrackPx.toFloat())
                                    .coerceIn(0f, 1f)
                                dragFraction = nextFrac
                                val targetIdx = (nextFrac * (totalItems - 1)).roundToInt()
                                    .coerceIn(0, (totalItems - 1).coerceAtLeast(0))
                                val dateAtIdx = indexToDatePill.getOrNull(targetIdx).orEmpty()
                                if (dateAtIdx.isNotEmpty() && dateAtIdx != lastHapticDate) {
                                    lastHapticDate = dateAtIdx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                scope.launch { gridState.scrollToItem(targetIdx) }
                            }
                        )
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "Fast scroll",
                        tint = if (isDragging) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            tokens.contentColor
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineMediaItem(
    medium: Medium,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    isHidden: Boolean,
    isLockedItem: Boolean,
    onClick: () -> Unit
) {
    val thumbnailBitmap = rememberThumbnail(medium, sizePx = NativeThumbnailLoader.DEFAULT_THUMB_PX)

    val selectionScale = if (isMultiSelectMode) {
        animateFloatAsState(
            targetValue = if (isSelected) 0.86f else 1f,
            animationSpec = LuminaMotion.spatialStandard(),
            label = "tile_selection_scale"
        ).value
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = selectionScale
                    scaleY = selectionScale
                    alpha = if (isHidden) 0f else 1f
                    if (isSelected) {
                        clip = true
                        shape = SelectedTileShape
                    }
                }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap,
                    contentDescription = medium.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isLockedItem) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.68f), CircleShape)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted hidden item",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            if (medium.isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        if (isMultiSelectMode) {
            val checkBg by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.42f),
                animationSpec = LuminaMotion.colorTransition,
                label = "tile_check_bg"
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(checkBg)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
