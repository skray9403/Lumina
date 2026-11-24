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

/**
 * Adaptive top chrome:
 * - On Home (alwaysFloating = false):
 *     • At scroll 0: clean header with "Lumina" (sans-serif) on left & circular ⋮ button on right.
 *     • On scroll: tight spring snap with slight overbounce into floating centered Date pill +
 *       floating ⋮ circle + status-bar-only feather halo.
 * - On Album / Smart Album overlays (alwaysFloating = true):
 *     • Always floating Back bubble + centered Album/Date pill + ⋮ bubble + status-bar halo.
 */
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
    showConfirmPick: Boolean,
    onBackClick: (() -> Unit)? = null,
    onColumnCountChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onConfirmPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val tokens = rememberLuminaPillTokens()
    val scrollTriggerPx = with(density) { 10.dp.roundToPx() }

    // Scoped derivedStateOf so scrolling across items never recomposes LuminaHomeScreen
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
        // 1. Status-bar-only feather halo (strictly visible once scrolled into floating form)
        StatusBarFeatherHalo(
            statusBarHeight = statusBarHeight,
            alpha = { floatingProgress },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 2. Chrome row pinned cleanly below status bar
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
                        tokens = tokens,
                        onBackClick = onBackClick,
                        onColumnCountChange = onColumnCountChange,
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
    tokens: LuminaPillTokens,
    onBackClick: (() -> Unit)?,
    onColumnCountChange: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    val density = LocalDensity.current
    var menuExpanded by remember { mutableStateOf(false) }

    val clamped = floatingProgress.coerceIn(0f, 1f)
    val jumpOffsetPx = with(density) { 6.dp.toPx() }
    val titleExitOffsetPx = with(density) { 14.dp.toPx() }
    val pillDropPx = with(density) { 10.dp.toPx() }

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
        // Left slot: either Back button (in album overlay) OR scroll-0 "Lumina" sans-serif title
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
            Text(
                text = headerTitle,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 23.sp,
                letterSpacing = (-0.4).sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .graphicsLayer {
                        alpha = (1f - clamped * 1.85f).coerceIn(0f, 1f)
                        translationY = -clamped * titleExitOffsetPx
                    }
            )
        }

        // Center slot: Floating Date Pill (snaps in with tight slight overbounce when scrolled)
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

        // Right slot: Single persistent ⋮ circle that snaps tightly into floating position on scroll
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer {
                    if (!alwaysFloating) {
                        translationY = floatingProgress * jumpOffsetPx
                        // Subtle tactile pop during transition settles back to 1f
                        val pop = 1f + (1f - kotlin.math.abs(clamped * 2f - 1f)) * 0.06f
                        scaleX = pop
                        scaleY = pop
                    }
                }
        ) {
            val menuInteraction = remember { MutableInteractionSource() }
            Surface(
                shape = CircleShape,
                color = tokens.containerColor,
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
                        tint = tokens.contentColor,
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
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    .size(38.dp)
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
