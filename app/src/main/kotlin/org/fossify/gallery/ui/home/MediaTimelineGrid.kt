package org.fossify.gallery.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.gallery.engine.image.rememberThumbnail
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.theme.LuminaMotion
import org.fossify.gallery.ui.theme.pressScale

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MediaTimelineGrid(
    sections: List<TimelineSection>,
    columnCount: Int,
    selectedIds: Set<Long>,
    isMultiSelectMode: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onMediaClick: (Medium) -> Unit,
    onMediaLongClick: (Medium) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount.coerceIn(2, 5)),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        sections.forEach { section ->
            item(
                span = { GridItemSpan(maxLineSpan) },
                key = "header_${section.title}",
                contentType = "date_header"
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                )
            }

            items(
                items = section.items,
                key = { it.id },
                contentType = { "media_item" }
            ) { medium ->
                val isSelected = medium.id in selectedIds
                TimelineMediaItem(
                    medium = medium,
                    isSelected = isSelected,
                    isMultiSelectMode = isMultiSelectMode,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
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
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun TimelineMediaItem(
    medium: Medium,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val thumbnailBitmap = rememberThumbnail(medium, sizePx = 360)
    val interactionSource = remember { MutableInteractionSource() }

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.86f else 1f,
        animationSpec = LuminaMotion.spatialStandard(),
        label = "tile_selection_scale"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 14.dp else 2.dp,
        animationSpec = LuminaMotion.spatialStandard(),
        label = "tile_selection_corner"
    )
    val checkBg by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Black.copy(alpha = 0.42f)
        },
        animationSpec = LuminaMotion.colorTransition,
        label = "tile_check_bg"
    )

    val sharedModifier = with(sharedTransitionScope) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = "media_${medium.id}"),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = { _, _ -> LuminaMotion.spatialStandard() }
        )
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
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = sharedModifier
                .fillMaxSize()
                .pressScale(interactionSource, pressedScale = 0.96f)
                .graphicsLayer {
                    scaleX = selectionScale
                    scaleY = selectionScale
                }
                .clip(RoundedCornerShape(cornerRadius))
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

            // Video indicator pill
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

        // Selection badge pinned to tile corner
        AnimatedVisibility(
            visible = isMultiSelectMode,
            enter = fadeIn(animationSpec = LuminaMotion.contentEnter),
            exit = fadeOut(animationSpec = LuminaMotion.contentExit),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        ) {
            Box(
                modifier = Modifier
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
