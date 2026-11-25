package org.fossify.gallery.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import org.fossify.gallery.ui.theme.LuminaMotion
import org.fossify.gallery.ui.theme.LuminaPillTokens
import org.fossify.gallery.ui.theme.StatusBarFeatherHalo
import org.fossify.gallery.ui.theme.pressScale
import org.fossify.gallery.ui.theme.rememberLuminaPillTokens

private val TightSnapOverbounce = spring<Float>(
    dampingRatio = 0.66f,
    stiffness = 820f
)

@Composable
fun FloatingTopChrome(
    gridState: LazyGridState?,
    indexToDatePill: List<String>,
    headerTitle: String,
    staticFloatingTitle: String?,
    statusBarHeight: Dp,
    alwaysFloating: Boolean,
    columnCount: Int,
    mediaFilter: MediaFilter,
    groupBy: MediaGroupBy,
    sortOrder: SortOrder,
    allowFolderGrouping: Boolean,
    isHiddenShown: Boolean,
    hasHiddenFiles: Boolean,
    onBackClick: (() -> Unit)? = null,
    onColumnCountChange: (Int) -> Unit,
    onMediaFilterChange: (MediaFilter) -> Unit,
    onGroupByChange: (MediaGroupBy) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onToggleShowHidden: () -> Unit,
    onUnlockAllHiddenToApps: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val tokens = rememberLuminaPillTokens()
    val scrollTriggerPx = with(density) { 10.dp.roundToPx() }

    val isFloating by remember(gridState, alwaysFloating, scrollTriggerPx) {
        derivedStateOf {
            alwaysFloating || (
                gridState != null && (
                    gridState.firstVisibleItemIndex > 0 ||
                        gridState.firstVisibleItemScrollOffset > scrollTriggerPx
                )
            )
        }
    }

    val floatingProgress by animateFloatAsState(
        targetValue = if (isFloating) 1f else 0f,
        animationSpec = TightSnapOverbounce,
        label = "top_chrome_float_snap"
    )

    val clamped = floatingProgress.coerceIn(0f, 1f)
    val jumpOffsetPx = with(density) { 4.dp.toPx() }
    val titleExitOffsetPx = with(density) { 12.dp.toPx() }

    val barShadow by animateDpAsState(
        targetValue = if (floatingProgress > 0.5f) tokens.shadowElevation + 3.dp else tokens.shadowElevation,
        animationSpec = LuminaMotion.expressiveSnappy(),
        label = "top_bar_shadow"
    )

    var filterMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }

    val hasActiveViewModifier = mediaFilter != MediaFilter.ALL ||
        groupBy != MediaGroupBy.DATE ||
        sortOrder != SortOrder.DESCENDING

    Box(modifier = modifier.fillMaxWidth()) {
        StatusBarFeatherHalo(
            statusBarHeight = statusBarHeight,
            alpha = { floatingProgress },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(50.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // ZONE 1 (LEFT ANCHOR): Clean title at scroll 0 -> snaps into left-aligned context/date pill on scroll
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (onBackClick != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val backInteraction = remember { MutableInteractionSource() }
                        Surface(
                            shape = CircleShape,
                            color = tokens.containerColor,
                            border = tokens.border,
                            shadowElevation = tokens.shadowElevation,
                            modifier = Modifier
                                .size(44.dp)
                                .pressScale(backInteraction, pressedScale = 0.90f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = backInteraction,
                                        indication = ripple(bounded = true),
                                        role = Role.Button,
                                        onClick = onBackClick
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = tokens.contentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        StickyLeftContextPill(
                            gridState = gridState,
                            indexToDatePill = indexToDatePill,
                            staticTitle = staticFloatingTitle,
                            isHiddenShown = isHiddenShown,
                            tokens = tokens
                        )
                    }
                } else {
                    if (clamped < 0.96f) {
                        val displayHeader = buildString {
                            append(headerTitle)
                            when (mediaFilter) {
                                MediaFilter.PHOTOS -> append(" · Photos")
                                MediaFilter.VIDEOS -> append(" · Videos")
                                MediaFilter.ALL -> Unit
                            }
                            if (isHiddenShown) append(" · Unlocked")
                        }
                        Text(
                            text = displayHeader,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 23.sp,
                            letterSpacing = (-0.4).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 4.dp, end = 12.dp)
                                .graphicsLayer {
                                    alpha = (1f - clamped * 1.9f).coerceIn(0f, 1f)
                                    translationY = -clamped * titleExitOffsetPx
                                }
                        )
                    }

                    if (clamped > 0.04f) {
                        StickyLeftContextPill(
                            gridState = gridState,
                            indexToDatePill = indexToDatePill,
                            staticTitle = staticFloatingTitle,
                            isHiddenShown = isHiddenShown,
                            tokens = tokens,
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .graphicsLayer {
                                    val s = lerp(0.86f, 1f, floatingProgress)
                                    scaleX = s
                                    scaleY = s
                                    translationY = lerp(-jumpOffsetPx * 2f, jumpOffsetPx, floatingProgress)
                                    alpha = (clamped * 1.35f).coerceIn(0f, 1f)
                                }
                        )
                    }
                }
            }

            // ZONE 2 (RIGHT ANCHOR): Unified 2-action trailing capsule [ Tune (Filter/Group/Sort) | ⋮ ]
            Box(
                modifier = Modifier.graphicsLayer {
                    if (!alwaysFloating) {
                        translationY = floatingProgress * jumpOffsetPx
                        val pop = 1f + (1f - kotlin.math.abs(clamped * 2f - 1f)) * 0.05f
                        scaleX = pop
                        scaleY = pop
                    }
                }
            ) {
                Surface(
                    shape = CircleShape,
                    color = tokens.containerColor,
                    border = tokens.border,
                    shadowElevation = barShadow.coerceAtLeast(0.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left button: View Options (Filter / Group / Sort)
                        val filterInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .pressScale(filterInteraction, pressedScale = 0.88f)
                                .clip(CircleShape)
                                .background(
                                    if (hasActiveViewModifier) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable(
                                    interactionSource = filterInteraction,
                                    indication = ripple(bounded = true),
                                    role = Role.Button,
                                    onClick = { filterMenuOpen = true }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Filter, group & sort",
                                tint = if (hasActiveViewModifier) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    tokens.contentColor
                                },
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .width(1.dp)
                                .height(18.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        )

                        // Right button: Overflow (Columns, Hidden toggle, Unlock all to apps, Refresh)
                        val moreInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .pressScale(moreInteraction, pressedScale = 0.88f)
                                .clip(CircleShape)
                                .background(
                                    if (isHiddenShown) MaterialTheme.colorScheme.tertiaryContainer
                                    else Color.Transparent
                                )
                                .clickable(
                                    interactionSource = moreInteraction,
                                    indication = ripple(bounded = true),
                                    role = Role.Button,
                                    onClick = { moreMenuOpen = true }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = if (isHiddenShown) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    tokens.contentColor
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Popover 1: View Options (Show / Group by / Sort Asc-Desc)
                DropdownMenu(
                    expanded = filterMenuOpen,
                    onDismissRequest = { filterMenuOpen = false },
                    offset = DpOffset(0.dp, 8.dp),
                    shape = RoundedCornerShape(22.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    border = tokens.border
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Show",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MenuSegmentChip("All", mediaFilter == MediaFilter.ALL) {
                                onMediaFilterChange(MediaFilter.ALL)
                            }
                            MenuSegmentChip("Photos", mediaFilter == MediaFilter.PHOTOS) {
                                onMediaFilterChange(MediaFilter.PHOTOS)
                            }
                            MenuSegmentChip("Videos", mediaFilter == MediaFilter.VIDEOS) {
                                onMediaFilterChange(MediaFilter.VIDEOS)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Group by",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MenuSegmentChip("Date", groupBy == MediaGroupBy.DATE) {
                                onGroupByChange(MediaGroupBy.DATE)
                            }
                            MenuSegmentChip("Size", groupBy == MediaGroupBy.SIZE) {
                                onGroupByChange(MediaGroupBy.SIZE)
                            }
                            if (allowFolderGrouping) {
                                MenuSegmentChip("Folder", groupBy == MediaGroupBy.FOLDER) {
                                    onGroupByChange(MediaGroupBy.FOLDER)
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Sort order",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val descLabel = when (groupBy) {
                            MediaGroupBy.DATE -> "Newest"
                            MediaGroupBy.SIZE -> "Largest"
                            MediaGroupBy.FOLDER -> "Z – A"
                        }
                        val ascLabel = when (groupBy) {
                            MediaGroupBy.DATE -> "Oldest"
                            MediaGroupBy.SIZE -> "Smallest"
                            MediaGroupBy.FOLDER -> "A – Z"
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MenuIconSegmentChip(
                                icon = Icons.Default.ArrowDownward,
                                label = descLabel,
                                selected = sortOrder == SortOrder.DESCENDING,
                                onClick = { onSortOrderChange(SortOrder.DESCENDING) }
                            )
                            MenuIconSegmentChip(
                                icon = Icons.Default.ArrowUpward,
                                label = ascLabel,
                                selected = sortOrder == SortOrder.ASCENDING,
                                onClick = { onSortOrderChange(SortOrder.ASCENDING) }
                            )
                        }
                    }
                }

                // Popover 2: Clean Overflow (Columns, Inline Hidden Toggle, Unlock All to Apps, Refresh)
                DropdownMenu(
                    expanded = moreMenuOpen,
                    onDismissRequest = { moreMenuOpen = false },
                    offset = DpOffset(0.dp, 8.dp),
                    shape = RoundedCornerShape(22.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    border = tokens.border
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Grid columns",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (cols in 2..5) {
                                val selected = columnCount == cols
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                                        )
                                        .clickable(role = Role.RadioButton) {
                                            moreMenuOpen = false
                                            onColumnCountChange(cols)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$cols",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    DropdownMenuItem(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = if (isHiddenShown) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        text = {
                            Text(
                                text = if (isHiddenShown) "Re-lock hidden items" else "Show hidden items",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        onClick = {
                            moreMenuOpen = false
                            onToggleShowHidden()
                        }
                    )

                    if (hasHiddenFiles || isHiddenShown) {
                        DropdownMenuItem(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = "Unlock all hidden to apps",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = {
                                moreMenuOpen = false
                                onUnlockAllHiddenToApps()
                            }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    DropdownMenuItem(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        text = {
                            Text(
                                text = "Refresh library",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        onClick = {
                            moreMenuOpen = false
                            onRefresh()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuSegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun MenuIconSegmentChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun StickyLeftContextPill(
    gridState: LazyGridState?,
    indexToDatePill: List<String>,
    staticTitle: String?,
    isHiddenShown: Boolean,
    tokens: LuminaPillTokens,
    modifier: Modifier = Modifier
) {
    val label by remember(gridState, indexToDatePill, staticTitle) {
        derivedStateOf {
            when {
                staticTitle != null && (gridState == null || gridState.firstVisibleItemIndex == 0) -> staticTitle
                gridState != null && indexToDatePill.isNotEmpty() -> {
                    val idx = gridState.firstVisibleItemIndex.coerceIn(0, indexToDatePill.lastIndex)
                    indexToDatePill[idx]
                }
                staticTitle != null -> staticTitle
                else -> ""
            }
        }
    }

    if (label.isNotEmpty()) {
        Surface(
            shape = CircleShape,
            color = tokens.containerColor,
            border = tokens.border,
            shadowElevation = tokens.shadowElevation + 2.dp,
            modifier = modifier.height(42.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 18.dp)
            ) {
                if (isHiddenShown) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Hidden items visible",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = tokens.contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
