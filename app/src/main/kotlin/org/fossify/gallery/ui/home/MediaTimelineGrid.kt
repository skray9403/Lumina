package org.fossify.gallery.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.fossify.gallery.engine.image.NativeThumbnailLoader
import org.fossify.gallery.engine.image.rememberThumbnail
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.theme.LuminaMotion

private val SelectedTileShape = RoundedCornerShape(14.dp)

/**
 * Computes exact root-space bounds of any visible tile on demand directly from
 * LazyGridState.layoutInfo without attaching per-tile onGloballyPositioned callbacks.
 */
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
    columnCount: Int,
    gridState: LazyGridState,
    selectedIds: Set<Long>,
    isMultiSelectMode: Boolean,
    hiddenMediaId: Long?,
    onGridRootOffsetChange: (Offset) -> Unit,
    onMediaClick: (Medium) -> Unit,
    onMediaLongClick: (Medium) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val clampedColumns = columnCount.coerceIn(2, 5)

    // 2-row lookahead thumbnail prefetching ahead of the visible viewport
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

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(clampedColumns),
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                onGridRootOffsetChange(coords.positionInRoot())
            },
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
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
                onClick = {
                    if (isMultiSelectMode) {
                        onMediaLongClick(medium)
                    } else {
                        onMediaClick(medium)
                    }
                },
                onLongClick = { onMediaLongClick(medium) }
            )
        }
    }
}

@Composable
private fun TimelineMediaItem(
    medium: Medium,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    isHidden: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
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
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Black.copy(alpha = 0.42f)
                },
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
