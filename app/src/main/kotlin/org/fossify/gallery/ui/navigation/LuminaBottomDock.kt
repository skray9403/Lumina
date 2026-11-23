package org.fossify.gallery.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoSizeSelectActual
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.home.GalleryTab
import org.fossify.gallery.ui.theme.LuminaMotion
import org.fossify.gallery.ui.theme.pressScale
import org.fossify.gallery.ui.theme.rememberLuminaDarkColorScheme

@Immutable
sealed interface DockMode {
    data class Home(
        val currentTab: GalleryTab,
        val isCollapsed: Boolean,
        val showScrollToTop: Boolean,
        val isSearchActive: Boolean,
        val onTabSelect: (GalleryTab) -> Unit,
        val onSearchToggle: () -> Unit,
        val onScrollToTop: () -> Unit
    ) : DockMode

    data class Selection(
        val selectedCount: Int,
        val onShare: () -> Unit,
        val onFavorite: () -> Unit,
        val onDelete: () -> Unit
    ) : DockMode

    data class Viewer(
        val medium: Medium,
        val isFavorite: Boolean,
        val onFavoriteToggle: () -> Unit,
        val onDetailsClick: () -> Unit,
        val onDeleteClick: () -> Unit
    ) : DockMode
}

private enum class UtilityPillAction {
    SEARCH,
    CLOSE_SEARCH,
    SCROLL_TOP
}

/**
 * Single persistent floating dock mounted once in LuminaApp.
 * - Through-zero mode transitions (no ghosted icon overlaps).
 * - Collapses tab labels into compact capsule on fast downward scroll (> 600 dp/s).
 * - Detached circular context pill for Search / Scroll-to-Top with zero vertical jump.
 */
@Composable
fun LuminaBottomDock(
    mode: DockMode,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isViewer = mode is DockMode.Viewer

    val darkDynamicScheme = rememberLuminaDarkColorScheme(dynamicColor = true)
    val activeScheme = if (isViewer) darkDynamicScheme else MaterialTheme.colorScheme

    val hiddenOffsetPx = with(density) { 120.dp.toPx() }
    val slideProgress by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = LuminaMotion.chromeSlide,
        label = "dock_visibility_slide"
    )

    val containerColor by animateColorAsState(
        targetValue = activeScheme.surfaceContainerHigh.copy(alpha = if (isViewer) 0.92f else 0.96f),
        animationSpec = LuminaMotion.colorTransition,
        label = "dock_container_color"
    )
    val borderColor by animateColorAsState(
        targetValue = activeScheme.outlineVariant.copy(alpha = if (isViewer) 0.28f else 0.35f),
        animationSpec = LuminaMotion.colorTransition,
        label = "dock_border_color"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .graphicsLayer {
                translationY = slideProgress * hiddenOffsetPx
                alpha = (1f - slideProgress * 1.15f).coerceIn(0f, 1f)
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Primary morphing dock capsule
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = containerColor,
            border = BorderStroke(0.75.dp, borderColor),
            shadowElevation = if (isViewer) 10.dp else 6.dp,
            modifier = Modifier
                .height(54.dp)
                .animateContentSize(animationSpec = LuminaMotion.dockSizeSpec)
        ) {
            AnimatedContent(
                targetState = mode,
                contentKey = { it::class },
                transitionSpec = {
                    fadeIn(animationSpec = LuminaMotion.throughZeroEnter) togetherWith
                        fadeOut(animationSpec = LuminaMotion.throughZeroExit)
                },
                label = "dock_mode_morph"
            ) { targetMode ->
                when (targetMode) {
                    is DockMode.Home -> HomeDockContent(
                        currentTab = targetMode.currentTab,
                        isCollapsed = targetMode.isCollapsed,
                        onTabSelect = targetMode.onTabSelect
                    )
                    is DockMode.Selection -> SelectionDockContent(
                        onShare = targetMode.onShare,
                        onFavorite = targetMode.onFavorite,
                        onDelete = targetMode.onDelete
                    )
                    is DockMode.Viewer -> ViewerDockContent(
                        medium = targetMode.medium,
                        isFavorite = targetMode.isFavorite,
                        onSurfaceTint = darkDynamicScheme.onSurface,
                        favoriteActiveTint = darkDynamicScheme.primary,
                        deleteTint = darkDynamicScheme.error,
                        onFavoriteToggle = targetMode.onFavoriteToggle,
                        onDetailsClick = targetMode.onDetailsClick,
                        onDeleteClick = targetMode.onDeleteClick,
                        context = context
                    )
                }
            }
        }

        // Detached Search / Scroll-to-Top circular context pill (Home mode only)
        val homeMode = mode as? DockMode.Home
        AnimatedVisibility(
            visible = homeMode != null,
            enter = fadeIn(LuminaMotion.throughZeroEnter) + expandHorizontally(LuminaMotion.dockSizeSpec),
            exit = fadeOut(LuminaMotion.throughZeroExit) + shrinkHorizontally(LuminaMotion.dockSizeSpec)
        ) {
            if (homeMode != null) {
                val utilityAction = when {
                    homeMode.isSearchActive -> UtilityPillAction.CLOSE_SEARCH
                    homeMode.showScrollToTop -> UtilityPillAction.SCROLL_TOP
                    else -> UtilityPillAction.SEARCH
                }
                DetachedUtilityPill(
                    action = utilityAction,
                    containerColor = containerColor,
                    borderColor = borderColor,
                    onClick = {
                        when (utilityAction) {
                            UtilityPillAction.SCROLL_TOP -> homeMode.onScrollToTop()
                            UtilityPillAction.SEARCH,
                            UtilityPillAction.CLOSE_SEARCH -> homeMode.onSearchToggle()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DetachedUtilityPill(
    action: UtilityPillAction,
    containerColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isAccent = action == UtilityPillAction.SCROLL_TOP || action == UtilityPillAction.CLOSE_SEARCH
    val pillBg by animateColorAsState(
        targetValue = if (isAccent) MaterialTheme.colorScheme.primaryContainer else containerColor,
        animationSpec = LuminaMotion.colorTransition,
        label = "utility_pill_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isAccent) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = LuminaMotion.colorTransition,
        label = "utility_pill_tint"
    )

    Surface(
        shape = CircleShape,
        color = pillBg,
        border = BorderStroke(0.75.dp, borderColor),
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(54.dp)
            .pressScale(interactionSource, pressedScale = 0.88f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = action,
                transitionSpec = {
                    fadeIn(LuminaMotion.throughZeroEnter) togetherWith
                        fadeOut(LuminaMotion.throughZeroExit)
                },
                label = "utility_pill_icon"
            ) { state ->
                val (icon, desc) = when (state) {
                    UtilityPillAction.SEARCH -> Icons.Default.Search to "Search"
                    UtilityPillAction.CLOSE_SEARCH -> Icons.Default.Close to "Close search"
                    UtilityPillAction.SCROLL_TOP -> Icons.Default.KeyboardArrowUp to "Scroll to top"
                }
                Icon(
                    imageVector = icon,
                    contentDescription = desc,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeDockContent(
    currentTab: GalleryTab,
    isCollapsed: Boolean,
    onTabSelect: (GalleryTab) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockTabPill(
            title = "Photos",
            icon = Icons.Default.PhotoSizeSelectActual,
            isSelected = currentTab == GalleryTab.PHOTOS,
            isCollapsed = isCollapsed,
            onClick = { onTabSelect(GalleryTab.PHOTOS) }
        )
        DockTabPill(
            title = "Albums",
            icon = Icons.Default.PhotoLibrary,
            isSelected = currentTab == GalleryTab.ALBUMS,
            isCollapsed = isCollapsed,
            onClick = { onTabSelect(GalleryTab.ALBUMS) }
        )
    }
}

@Composable
private fun DockTabPill(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    isCollapsed: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = LuminaMotion.colorTransition,
        label = "pill_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = LuminaMotion.colorTransition,
        label = "pill_content"
    )

    Row(
        modifier = Modifier
            .height(44.dp)
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(26.dp))
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = if (isCollapsed) 14.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCollapsed) 0.dp else 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        AnimatedVisibility(
            visible = !isCollapsed,
            enter = fadeIn(LuminaMotion.throughZeroEnter) + expandHorizontally(LuminaMotion.dockSizeSpec),
            exit = fadeOut(LuminaMotion.throughZeroExit) + shrinkHorizontally(LuminaMotion.dockSizeSpec)
        ) {
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun SelectionDockContent(
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockActionButton(
            icon = Icons.Default.Share,
            contentDescription = "Share selected",
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onShare
        )
        DockActionButton(
            icon = Icons.Default.FavoriteBorder,
            contentDescription = "Favorite selected",
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onFavorite
        )
        DockActionButton(
            icon = Icons.Default.Delete,
            contentDescription = "Delete selected",
            tint = MaterialTheme.colorScheme.error,
            onClick = onDelete
        )
    }
}

@Composable
private fun ViewerDockContent(
    medium: Medium,
    isFavorite: Boolean,
    onSurfaceTint: Color,
    favoriteActiveTint: Color,
    deleteTint: Color,
    onFavoriteToggle: () -> Unit,
    onDetailsClick: () -> Unit,
    onDeleteClick: () -> Unit,
    context: Context
) {
    val favoriteTint by animateColorAsState(
        targetValue = if (isFavorite) favoriteActiveTint else onSurfaceTint,
        animationSpec = LuminaMotion.colorTransition,
        label = "viewer_favorite_tint"
    )

    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockActionButton(
            icon = Icons.Default.Share,
            contentDescription = "Share",
            tint = onSurfaceTint,
            onClick = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, medium.uri)
                    type = medium.mimeType
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share with"))
            }
        )
        DockActionButton(
            icon = Icons.Default.Edit,
            contentDescription = "Edit externally",
            tint = onSurfaceTint,
            onClick = {
                val editIntent = Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(medium.uri, medium.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(editIntent, "Edit with"))
            }
        )
        DockActionButton(
            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "Favorite",
            tint = favoriteTint,
            onClick = onFavoriteToggle
        )
        DockActionButton(
            icon = Icons.Default.Info,
            contentDescription = "Details",
            tint = onSurfaceTint,
            onClick = onDetailsClick
        )
        DockActionButton(
            icon = Icons.Default.Delete,
            contentDescription = "Delete",
            tint = deleteTint,
            onClick = onDeleteClick
        )
    }
}

@Composable
private fun DockActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(44.dp)
            .pressScale(interactionSource, pressedScale = 0.88f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
