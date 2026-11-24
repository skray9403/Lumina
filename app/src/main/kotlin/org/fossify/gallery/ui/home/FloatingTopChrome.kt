package org.fossify.gallery.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.graphicsLayer
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
    isMultiSelectMode: Boolean,
    selectedCount: Int,
    columnCount: Int,
    mediaFilter: MediaFilter,
    groupBy: MediaGroupBy,
    allowFolderGrouping: Boolean,
    showConfirmPick: Boolean,
    onBackClick: (() -> Unit)? = null,
    onColumnCountChange: (Int) -> Unit,
    onMediaFilterChange: (MediaFilter) -> Unit,
    onGroupByChange: (MediaGroupBy) -> Unit,
    onRefresh: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onConfirmPick: () -> Unit,
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
        targetValue = if (isFloating || isMultiSelectMode) 1f else 0f,
        animationSpec = TightSnapOverbounce,
        label = "top_chrome_float_snap"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        StatusBarFeatherHalo(
            statusBarHeight = statusBarHeight,
            alpha = { floatingProgress },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(52.dp)
        ) {
            AnimatedContent(
                targetState = isMultiSelectMode,
                transitionSpec = {
                    fadeIn(LuminaMotion.throughZeroEnter) togetherWith
                        fadeOut(LuminaMotion.throughZeroExit)
                },
                label = "top_chrome_selection_swap",
                modifier = Modifier.fillMaxWidth()
            ) { selecting ->
                if (selecting) {
                    SelectionTopBar(
                        selectedCount = selectedCount,
                        showConfirmPick = showConfirmPick,
                        onClearSelection = onClearSelection,
                        onSelectAll = onSelectAll,
                        onConfirmPick = onConfirmPick
                    )
                } else {
                    MorphingHeaderAndPills(
                        gridState = gridState,
                        indexToDatePill = indexToDatePill,
                        headerTitle = headerTitle,
                        staticFloatingTitle = staticFloatingTitle,
                        alwaysFloating = alwaysFloating,
                        floatingProgress = floatingProgress,
                        columnCount = columnCount,
                        mediaFilter = mediaFilter,
                        groupBy = groupBy,
                        allowFolderGrouping = allowFolderGrouping,
                        tokens = tokens,
                        onBackClick = onBackClick,
                        onColumnCountChange = onColumnCountChange,
                        onMediaFilterChange = onMediaFilterChange,
                        onGroupByChange = onGroupByChange,
                        onRefresh = onRefresh
                    )
                }
            }
        }
    }
}

@Composable
private fun MorphingHeaderAndPills(
    gridState: LazyGridState?,
    indexToDatePill: List<String>,
    headerTitle: String,
    staticFloatingTitle: String?,
    alwaysFloating: Boolean,
    floatingProgress: Float,
    columnCount: Int,
    mediaFilter: MediaFilter,
    groupBy: MediaGroupBy,
    allowFolderGrouping: Boolean,
    tokens: LuminaPillTokens,
    onBackClick: (() -> Unit)?,
    onColumnCountChange: (Int) -> Unit,
    onMediaFilterChange: (MediaFilter) -> Unit,
    onGroupByChange: (MediaGroupBy) -> Unit,
    onRefresh: () -> Unit
) {
    val density = LocalDensity.current
    var menuExpanded by remember { mutableStateOf(false) }

    val clamped = floatingProgress.coerceIn(0f, 1f)
    val jumpOffsetPx = with(density) { 6.dp.toPx() }
    val titleExitOffsetPx = with(density) { 14.dp.toPx() }
    val pillDropPx = with(density) { 10.dp.toPx() }

    val hasActiveFilterOrGroup = mediaFilter != MediaFilter.ALL || groupBy != MediaGroupBy.DATE
    val menuBg = if (hasActiveFilterOrGroup) MaterialTheme.colorScheme.primaryContainer else tokens.containerColor
    val menuFg = if (hasActiveFilterOrGroup) MaterialTheme.colorScheme.onPrimaryContainer else tokens.contentColor

    val menuShadow by animateDpAsState(
        targetValue = if (floatingProgress > 0.5f) tokens.shadowElevation + 3.dp else tokens.shadowElevation,
        animationSpec = LuminaMotion.expressiveSnappy(),
        label = "menu_shadow_snap"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (onBackClick != null) {
            val backInteraction = remember { MutableInteractionSource() }
            Surface(
                shape = CircleShape,
                color = tokens.containerColor,
                border = tokens.border,
                shadowElevation = tokens.shadowElevation,
                modifier = Modifier
                    .align(Alignment.CenterStart)
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
        } else if (!alwaysFloating && clamped < 0.98f) {
            val displayHeader = when (mediaFilter) {
                MediaFilter.PHOTOS -> "$headerTitle · Photos"
                MediaFilter.VIDEOS -> "$headerTitle · Videos"
                MediaFilter.ALL -> headerTitle
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
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp, end = 56.dp)
                    .graphicsLayer {
                        alpha = (1f - clamped * 1.85f).coerceIn(0f, 1f)
                        translationY = -clamped * titleExitOffsetPx
                    }
            )
        }

        if (alwaysFloating || clamped > 0.02f) {
            StickyCenterDatePill(
                gridState = gridState,
                indexToDatePill = indexToDatePill,
                staticTitle = staticFloatingTitle,
                tokens = tokens,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        if (!alwaysFloating) {
                            val s = lerp(0.84f, 1f, floatingProgress)
                            scaleX = s
                            scaleY = s
                            translationY = lerp(-pillDropPx, jumpOffsetPx, floatingProgress)
                            alpha = (clamped * 1.35f).coerceIn(0f, 1f)
                        }
                    }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer {
                    if (!alwaysFloating) {
                        translationY = floatingProgress * jumpOffsetPx
                        val pop = 1f + (1f - kotlin.math.abs(clamped * 2f - 1f)) * 0.06f
                        scaleX = pop
                        scaleY = pop
                    }
                }
        ) {
            val menuInteraction = remember { MutableInteractionSource() }
            Surface(
                shape = CircleShape,
                color = menuBg,
                border = tokens.border,
                shadowElevation = menuShadow.coerceAtLeast(0.dp),
                modifier = Modifier
                    .size(44.dp)
                    .pressScale(menuInteraction, pressedScale = 0.90f)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = menuInteraction,
                            indication = ripple(bounded = true),
                            role = Role.Button,
                            onClick = { menuExpanded = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Gallery options",
                        tint = menuFg,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                offset = DpOffset(0.dp, 8.dp),
                shape = RoundedCornerShape(22.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = tokens.border
            ) {
                // 1. Show: All | Photos | Videos
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Show",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MenuSegmentChip(
                            label = "All",
                            selected = mediaFilter == MediaFilter.ALL,
                            onClick = {
                                onMediaFilterChange(MediaFilter.ALL)
                                menuExpanded = false
                            }
                        )
                        MenuSegmentChip(
                            label = "Photos",
                            selected = mediaFilter == MediaFilter.PHOTOS,
                            onClick = {
                                onMediaFilterChange(MediaFilter.PHOTOS)
                                menuExpanded = false
                            }
                        )
                        MenuSegmentChip(
                            label = "Videos",
                            selected = mediaFilter == MediaFilter.VIDEOS,
                            onClick = {
                                onMediaFilterChange(MediaFilter.VIDEOS)
                                menuExpanded = false
                            }
                        )
                    }
                }

                // 2. Group by: Date | Size | Folder
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Group by",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MenuSegmentChip(
                            label = "Date",
                            selected = groupBy == MediaGroupBy.DATE,
                            onClick = {
                                onGroupByChange(MediaGroupBy.DATE)
                                menuExpanded = false
                            }
                        )
                        MenuSegmentChip(
                            label = "Size",
                            selected = groupBy == MediaGroupBy.SIZE,
                            onClick = {
                                onGroupByChange(MediaGroupBy.SIZE)
                                menuExpanded = false
                            }
                        )
                        if (allowFolderGrouping) {
                            MenuSegmentChip(
                                label = "Folder",
                                selected = groupBy == MediaGroupBy.FOLDER,
                                onClick = {
                                    onGroupByChange(MediaGroupBy.FOLDER)
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }

                // 3. Grid columns: 2 | 3 | 4 | 5
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Grid columns",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp)
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
                                        menuExpanded = false
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
                        menuExpanded = false
                        onRefresh()
                    }
                )
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
private fun StickyCenterDatePill(
    gridState: LazyGridState?,
    indexToDatePill: List<String>,
    staticTitle: String?,
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
            shape = RoundedCornerShape(24.dp),
            color = tokens.containerColor,
            border = tokens.border,
            shadowElevation = tokens.shadowElevation + 2.dp,
            modifier = modifier.height(42.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
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
