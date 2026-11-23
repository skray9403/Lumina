package org.fossify.gallery.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.fossify.gallery.engine.share.QuickShareTarget
import org.fossify.gallery.engine.share.RecentShareStore
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.home.GalleryTab
import org.fossify.gallery.ui.theme.LuminaMotion
import org.fossify.gallery.ui.theme.pressScale
import org.fossify.gallery.ui.theme.rememberLuminaDarkColorScheme
import org.fossify.gallery.ui.theme.rememberLuminaPillTokens
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
sealed interface DockMode {
    data class Home(
        val currentTab: GalleryTab,
        val isCollapsed: Boolean,
        val showScrollToTop: Boolean,
        val isSearchActive: Boolean,
        val searchQuery: String,
        val activeAlbumName: String?,
        val onTabSelect: (GalleryTab) -> Unit,
        val onSearchOpen: () -> Unit,
        val onSearchClose: () -> Unit,
        val onSearchQueryChange: (String) -> Unit,
        val onScrollToTop: () -> Unit
    ) : DockMode

    data class Selection(
        val selectedCount: Int,
        val onShare: () -> Unit,
        val onAddToAlbum: () -> Unit,
        val onDelete: () -> Unit
    ) : DockMode

    data class Viewer(
        val medium: Medium,
        val onAddToAlbumClick: () -> Unit,
        val onDeleteClick: () -> Unit
    ) : DockMode
}

private enum class UtilityPillAction {
    SEARCH,
    SCROLL_TOP
}

/**
 * Persistent floating bottom dock.
 * - Shadow-safe layout (never clips shadowed Surfaces).
 * - Search expands in-place across the full bottom bar and rises with imePadding().
 * - Viewer mode uses decluttered [Edit | + Add to | Delete] + detached Smart Share FAB.
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
    val homeMode = mode as? DockMode.Home
    val isSearchExpanded = homeMode?.isSearchActive == true

    val darkDynamicScheme = rememberLuminaDarkColorScheme(dynamicColor = true)
    val pillTokens = rememberLuminaPillTokens(
        darkOverride = if (isViewer) true else null,
        colorSchemeOverride = if (isViewer) darkDynamicScheme else null
    )

    val hiddenOffsetPx = with(density) { 120.dp.toPx() }
    val slideProgress by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = LuminaMotion.chromeSlide,
        label = "dock_visibility_slide"
    )

    val containerColor by animateColorAsState(
        targetValue = pillTokens.containerColor,
        animationSpec = LuminaMotion.colorTransition,
        label = "dock_container_color"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .graphicsLayer {
                translationY = slideProgress * hiddenOffsetPx
                alpha = (1f - slideProgress * 1.15f).coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        if (isSearchExpanded && homeMode != null) {
            // Expanded bottom search pill occupying the entire bottom dock width
            BottomSearchBarPill(
                query = homeMode.searchQuery,
                placeholder = if (homeMode.activeAlbumName != null) {
                    "Search in ${homeMode.activeAlbumName}..."
                } else {
                    "Search photos, videos, albums..."
                },
                containerColor = containerColor,
                contentColor = pillTokens.contentColor,
                secondaryColor = pillTokens.secondaryContentColor,
                border = pillTokens.border,
                shadowElevation = pillTokens.shadowElevation,
                onQueryChange = homeMode.onSearchQueryChange,
                onClose = homeMode.onSearchClose
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary morphing capsule (animateContentSize lives INSIDE Surface to avoid clipping shadow)
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = containerColor,
                    border = pillTokens.border,
                    shadowElevation = pillTokens.shadowElevation,
                    modifier = Modifier.height(54.dp)
                ) {
                    Box(
                        modifier = Modifier.animateContentSize(animationSpec = LuminaMotion.dockSizeSpec),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = mode,
                            contentKey = { it::class },
                            transitionSpec = {
                                fadeIn(LuminaMotion.throughZeroEnter) togetherWith
                                    fadeOut(LuminaMotion.throughZeroExit)
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
                                    onAddToAlbum = targetMode.onAddToAlbum,
                                    onDelete = targetMode.onDelete
                                )

                                is DockMode.Viewer -> ViewerDockContent(
                                    medium = targetMode.medium,
                                    onSurfaceTint = darkDynamicScheme.onSurface,
                                    deleteTint = darkDynamicScheme.error,
                                    onAddToAlbumClick = targetMode.onAddToAlbumClick,
                                    onDeleteClick = targetMode.onDeleteClick,
                                    context = context
                                )
                            }
                        }
                    }
                }

                // Trailing detached circular FAB slot (Search/ScrollTop in Home, Smart Share FAB in Viewer)
                when (mode) {
                    is DockMode.Home -> {
                        val action = if (mode.showScrollToTop) {
                            UtilityPillAction.SCROLL_TOP
                        } else {
                            UtilityPillAction.SEARCH
                        }
                        DetachedUtilityFab(
                            action = action,
                            containerColor = containerColor,
                            contentColor = pillTokens.contentColor,
                            border = pillTokens.border,
                            shadowElevation = pillTokens.shadowElevation,
                            onClick = {
                                if (action == UtilityPillAction.SCROLL_TOP) {
                                    mode.onScrollToTop()
                                } else {
                                    mode.onSearchOpen()
                                }
                            }
                        )
                    }

                    is DockMode.Viewer -> {
                        SmartShareFab(
                            medium = mode.medium,
                            containerColor = darkDynamicScheme.primaryContainer,
                            contentColor = darkDynamicScheme.onPrimaryContainer,
                            popupContainerColor = containerColor,
                            popupContentColor = darkDynamicScheme.onSurface,
                            border = pillTokens.border,
                            shadowElevation = pillTokens.shadowElevation
                        )
                    }

                    is DockMode.Selection -> Unit
                }
            }
        }
    }
}

@Composable
private fun BottomSearchBarPill(
    query: String,
    placeholder: String,
    containerColor: Color,
    contentColor: Color,
    secondaryColor: Color,
    border: BorderStroke,
    shadowElevation: androidx.compose.ui.unit.Dp,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        border = border,
        shadowElevation = shadowElevation,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = secondaryColor,
                modifier = Modifier.size(20.dp)
            )

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            val closeInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .pressScale(closeInteraction, pressedScale = 0.88f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = closeInteraction,
                        indication = ripple(bounded = true),
                        role = Role.Button,
                        onClick = {
                            if (query.isNotEmpty()) {
                                onQueryChange("")
                            } else {
                                focusManager.clearFocus()
                                onClose()
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = if (query.isNotEmpty()) "Clear query" else "Close search",
                    tint = secondaryColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DetachedUtilityFab(
    action: UtilityPillAction,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke,
    shadowElevation: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isScrollTop = action == UtilityPillAction.SCROLL_TOP
    val bg by animateColorAsState(
        targetValue = if (isScrollTop) MaterialTheme.colorScheme.primaryContainer else containerColor,
        animationSpec = LuminaMotion.colorTransition,
        label = "utility_fab_bg"
    )
    val tint by animateColorAsState(
        targetValue = if (isScrollTop) MaterialTheme.colorScheme.onPrimaryContainer else contentColor,
        animationSpec = LuminaMotion.colorTransition,
        label = "utility_fab_tint"
    )

    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        shadowElevation = shadowElevation,
        modifier = Modifier
            .size(54.dp)
            .pressScale(interactionSource, pressedScale = 0.88f)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = action,
                transitionSpec = {
                    fadeIn(LuminaMotion.throughZeroEnter) togetherWith
                        fadeOut(LuminaMotion.throughZeroExit)
                },
                label = "utility_fab_icon"
            ) { target ->
                Icon(
                    imageVector = if (target == UtilityPillAction.SCROLL_TOP) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.Search
                    },
                    contentDescription = if (target == UtilityPillAction.SCROLL_TOP) {
                        "Scroll to top"
                    } else {
                        "Search"
                    },
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Viewer Share FAB:
 * - Normal tap: Native Android Sharesheet (updates recent apps via ShareTargetReceiver).
 * - Swipe up: Floating vertical stack of recently shared apps; drag-release or tap to share directly.
 */
@Composable
private fun SmartShareFab(
    medium: Medium,
    containerColor: Color,
    contentColor: Color,
    popupContainerColor: Color,
    popupContentColor: Color,
    border: BorderStroke,
    shadowElevation: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }

    var recentTargets by remember { mutableStateOf<List<QuickShareTarget>>(emptyList()) }
    var isQuickMenuOpen by remember { mutableStateOf(false) }
    var hoveredIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(medium.mimeType, isQuickMenuOpen) {
        recentTargets = RecentShareStore.loadRecentTargets(context, medium.mimeType)
    }

    // Reset quick menu on page swipe
    LaunchedEffect(medium.id) {
        isQuickMenuOpen = false
        hoveredIndex = -1
    }

    val itemStepPx = with(density) { 48.dp.toPx() }
    val swipeTriggerPx = with(density) { 26.dp.toPx() }

    Box(contentAlignment = Alignment.BottomCenter) {
        // Floating recent-apps vertical stack above Share FAB
        if (isQuickMenuOpen && recentTargets.isNotEmpty()) {
            val popupBottomOffsetPx = with(density) { 64.dp.roundToPx() }
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = popupContainerColor,
                border = border,
                shadowElevation = shadowElevation,
                modifier = Modifier
                    .offset { IntOffset(0, -popupBottomOffsetPx) }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Display top-to-bottom so index 0 (most recent) sits closest to the thumb
                    recentTargets.asReversed().forEachIndexed { visualIdx, target ->
                        val logicalIdx = recentTargets.lastIndex - visualIdx
                        val isHovered = logicalIdx == hoveredIndex
                        val itemScale by animateFloatAsState(
                            targetValue = if (isHovered) 1.18f else 1f,
                            animationSpec = LuminaMotion.responsiveSpring(),
                            label = "quick_share_scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .graphicsLayer {
                                    scaleX = itemScale
                                    scaleY = itemScale
                                }
                                .clip(CircleShape)
                                .background(
                                    if (isHovered) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else Color.Transparent
                                )
                                .clickable(role = Role.Button) {
                                    isQuickMenuOpen = false
                                    hoveredIndex = -1
                                    RecentShareStore.shareDirectlyTo(context, medium, target)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = target.icon,
                                contentDescription = target.label,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = containerColor,
            border = border,
            shadowElevation = shadowElevation,
            modifier = Modifier
                .size(54.dp)
                .pressScale(interactionSource, pressedScale = 0.88f)
                .pointerInput(medium.id, recentTargets) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startY = down.position.y
                        var swipedUp = false
                        var movedPastSlop = false

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            val dy = change.position.y - startY

                            if (abs(dy) > viewConfiguration.touchSlop) {
                                movedPastSlop = true
                            }

                            if (dy < -swipeTriggerPx && recentTargets.isNotEmpty()) {
                                if (!swipedUp) {
                                    swipedUp = true
                                    isQuickMenuOpen = true
                                }
                                val liftAboveFab = (-dy - swipeTriggerPx).coerceAtLeast(0f)
                                val idx = (liftAboveFab / itemStepPx).toInt()
                                hoveredIndex = if (idx in recentTargets.indices) idx else -1
                                change.consume()
                            } else if (swipedUp) {
                                hoveredIndex = -1
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        if (swipedUp) {
                            val chosen = recentTargets.getOrNull(hoveredIndex)
                            hoveredIndex = -1
                            if (chosen != null) {
                                isQuickMenuOpen = false
                                RecentShareStore.shareDirectlyTo(context, medium, chosen)
                            }
                        } else if (!movedPastSlop) {
                            if (isQuickMenuOpen) {
                                isQuickMenuOpen = false
                            } else {
                                RecentShareStore.launchNativeShareSheet(context, medium)
                            }
                        }
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isQuickMenuOpen) Icons.Default.Close else Icons.Default.Share,
                    contentDescription = "Share (swipe up for recent apps)",
                    tint = contentColor,
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
        modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
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
        if (!isCollapsed) {
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SelectionDockContent(
    onShare: () -> Unit,
    onAddToAlbum: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockActionButton(
            icon = Icons.Default.Share,
            contentDescription = "Share selected",
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onShare
        )
        DockLabeledActionButton(
            icon = Icons.Default.Add,
            label = "Add to",
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onAddToAlbum
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
    onSurfaceTint: Color,
    deleteTint: Color,
    onAddToAlbumClick: () -> Unit,
    onDeleteClick: () -> Unit,
    context: Context
) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockActionButton(
            icon = Icons.Default.Edit,
            contentDescription = "Edit",
            tint = onSurfaceTint,
            onClick = {
                val editIntent = Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(medium.uri, medium.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(editIntent, "Edit with"))
            }
        )
        DockLabeledActionButton(
            icon = Icons.Default.Add,
            label = "Add to",
            tint = onSurfaceTint,
            onClick = onAddToAlbumClick
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
private fun DockLabeledActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .height(44.dp)
            .widthIn(min = 44.dp)
            .pressScale(interactionSource, pressedScale = 0.90f)
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1
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
