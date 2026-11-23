package org.fossify.gallery.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import org.fossify.gallery.engine.image.NativeThumbnailLoader
import org.fossify.gallery.engine.image.rememberThumbnail
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.theme.LuminaMotion
import org.fossify.gallery.ui.theme.pressScale

private val SelectedTileShape = RoundedCornerShape(14.dp)

@Composable
fun MediaTimelineGrid(
    media: List<Medium>,
    columnCount: Int,
    gridState: LazyGridState,
    selectedIds: Set<Long>,
    isMultiSelectMode: Boolean,
    hiddenMediaId: Long?,
    tileBounds: MutableMap<Long, Rect>,
    onMediaClick: (Medium, Rect?) -> Unit,
    onMediaLongClick: (Medium) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columnCount.coerceIn(2, 5)),
        modifier = modifier.fillMaxSize(),
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
                tileBounds = tileBounds,
                onClick = {
                    if (isMultiSelectMode) {
                        onMediaLongClick(medium)
                    } else {
                        onMediaClick(medium, tileBounds[medium.id])
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
    tileBounds: MutableMap<Long, Rect>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val thumbnailBitmap = rememberThumbnail(medium, sizePx = NativeThumbnailLoader.DEFAULT_THUMB_PX)
    val interactionSource = remember { MutableInteractionSource() }

    DisposableEffect(medium.id) {
        onDispose { tileBounds.remove(medium.id) }
    }

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
            .onGloballyPositioned { coords ->
                tileBounds[medium.id] = coords.boundsInRoot()
            }
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pressScale(interactionSource, pressedScale = 0.96f)
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
