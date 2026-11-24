package org.fossify.gallery.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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

private val ExpressiveSpatialEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

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

    val searchProgress by animateFloatAsState(
        targetValue = if (isSearchExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = ExpressiveSpatialEasing),
        label = "dock_search_morph"
    )

    val containerColor by animateColorAsState(
        targetValue = pillTokens.containerColor,
        animationSpec = LuminaMotion.colorTransition,
        label = "dock_container_color"
    )

    val barHeightPx = with(density) { 54.dp.roundToPx() }
    val slotGapPx = with(density) { 10.dp.roundToPx() }
    val hasTrailingSlot = mode is DockMode.Home || mode is DockMode.Viewer

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
        // Custom 2-slot morphing Layout: Search circle expands smoothly across full width while left pill fades/shrinks
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                // Child 0: Primary morphing capsule
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = containerColor,
                    border = pillTokens.border,
                    shadowElevation = pillTokens.shadowElevation,
                    modifier = Modifier.height(54.dp)
                ) {
                    AnimatedContent(
                        targetState = mode,
                        contentKey = { it::class },
                        transitionSpec = {
                            (fadeIn(LuminaMotion.throughZeroEnter) togetherWith
                                fadeOut(LuminaMotion.throughZeroExit))
                                .using(SizeTransform(clip = false) { _, _ -> LuminaMotion.dockSizeSpec })
                        },
                        contentAlignment = Alignment.Center,
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

                // Child 1: Trailing slot (Morphing Search/ScrollTop in Home; Popup-anchored Smart Share FAB in Viewer)
                if (hasTrailingSlot) {
                    when (mode) {
                        is DockMode.Home -> MorphingSearchOrFab(
                            searchProgress = searchProgress,
                            isSearchActive = mode.isSearchActive,
                            showScrollToTop = mode.showScrollToTop,
                            query = mode.searchQuery,
                            placeholder = if (mode.activeAlbumName != null) {
                                "Search in ${mode.activeAlbumName}..."
                            } else {
                                "Search photos, videos, albums..."
                            },
                            containerColor = containerColor,
                            contentColor = pillTokens.contentColor,
                            secondaryColor = pillTokens.secondaryContentColor,
                            border = pillTokens.border,
                            shadowElevation = pillTokens.shadowElevation,
                            onSearchOpen = mode.onSearchOpen,
                            onSearchClose = mode.onSearchClose,
                            onQueryChange = mode.onSearchQueryChange,
                            onScrollToTop = mode.onScrollToTop
                        )

                        is DockMode.Viewer -> SmartShareFab(
                            medium = mode.medium,
                            containerColor = darkDynamicScheme.primaryContainer,
                            contentColor = darkDynamicScheme.onPrimaryContainer,
                            popupContainerColor = containerColor,
                            popupContentColor = darkDynamicScheme.onSurface,
                            border = pillTokens.border,
                            shadowElevation = pillTokens.shadowElevation
                        )

                        is DockMode.Selection -> Unit
                    }
                }
            }
        ) { measurables, constraints ->
            val maxW = constraints.maxWidth
            val primaryMeasurable = measurables[0]
            val trailingMeasurable = measurables.getOrNull(1)

            val primaryPlaceable = primaryMeasurable.measure(
                Constraints(minWidth = 0, maxWidth = maxW, minHeight = barHeightPx, maxHeight = barHeightPx)
            )

            val trailingPlaceable = trailingMeasurable?.let { m ->
                val targetW = lerp(barHeightPx.toFloat(), maxW.toFloat(), searchProgress)
                    .roundToInt()
                    .coerceIn(barHeightPx, maxW)
                m.measure(Constraints.fixed(targetW, barHeightPx))
            }

            val totalRestW = if (trailingPlaceable != null) {
                primaryPlaceable.width + slotGapPx + barHeightPx
            } else {
                primaryPlaceable.width
            }
            val primaryRestX = ((maxW - totalRestW) / 2).coerceAtLeast(0)
            val trailingRestX = primaryRestX + primaryPlaceable.width + slotGapPx

            layout(maxW, barHeightPx) {
                if (searchProgress < 0.98f) {
                    primaryPlaceable.placeWithLayer(primaryRestX, 0) {
                        val s = lerp(1f, 0.88f, searchProgress)
                        scaleX = s
                        scaleY = s
                        alpha = (1f - searchProgress * 2.2f).coerceIn(0f, 1f)
                        translationX = -searchProgress * slotGapPx * 2f
                    }
                }
                if (trailingPlaceable != null) {
                    val trailingX = lerp(trailingRestX.toFloat(), 0f, searchProgress).roundToInt()
                    trailingPlaceable.placeRelative(trailingX, 0)
                }
            }
        }
    }
}

/**
 * Single Surface that smoothly expands from a 54dp circular Search/ScrollTop button into
 * a full-width bottom Search field. Uses local synchronous TextFieldValue so async ViewModel
 * filtering never resets cursor position to index 0.
 */
@Composable
private fun MorphingSearchOrFab(
    searchProgress: Float,
    isSearchActive: Boolean,
    showScrollToTop: Boolean,
    query: String,
    placeholder: String,
    containerColor: Color,
    contentColor: Color,
    secondaryColor: Color,
    border: BorderStroke,
    shadowElevation: Dp,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onScrollToTop: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // Local synchronous TextFieldValue eliminates cursor-jump-to-left during async filtering
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = query, selection = TextRange(query.length)))
    }

    LaunchedEffect(query) {
        if (query.isEmpty() && textFieldValue.text.isNotEmpty()) {
            textFieldValue = TextFieldValue(text = "", selection = TextRange.Zero)
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            textFieldValue = TextFieldValue(text = query, selection = TextRange(query.length))
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    val isAccentScrollTop = showScrollToTop && !isSearchActive && searchProgress < 0.1f
    val bg by animateColorAsState(
        targetValue = if (isAccentScrollTop) MaterialTheme.colorScheme.primaryContainer else containerColor,
        animationSpec = LuminaMotion.colorTransition,
        label = "search_fab_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isAccentScrollTop) MaterialTheme.colorScheme.onPrimaryContainer else contentColor,
        animationSpec = LuminaMotion.colorTransition,
        label = "search_fab_tint"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = bg,
        border = border,
        shadowElevation = shadowElevation,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!isSearchActive && searchProgress < 0.2f) {
                    Modifier.pressScale(interactionSource, pressedScale = 0.88f)
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .then(
                    if (!isSearchActive) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true),
                            role = Role.Button,
                            onClick = {
                                if (showScrollToTop) onScrollToTop() else onSearchOpen()
                            }
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Collapsed circular FAB icon (fades out cleanly as width expands)
            val fabAlpha = (1f - searchProgress * 3.2f).coerceIn(0f, 1f)
            if (fabAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = fabAlpha },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = showScrollToTop,
                        transitionSpec = {
                            fadeIn(LuminaMotion.throughZeroEnter) togetherWith
                                fadeOut(LuminaMotion.throughZeroExit)
                        },
                        label = "fab_icon_swap"
                    ) { scrollTop ->
                        Icon(
                            imageVector = if (scrollTop) Icons.Default.KeyboardArrowUp else Icons.Default.Search,
                            contentDescription = if (scrollTop) "Scroll to top" else "Search",
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Expanded Search Row (fades in smoothly as capsule opens to full width)
            val fieldAlpha = ((searchProgress - 0.28f) / 0.72f).coerceIn(0f, 1f)
            if (fieldAlpha > 0.01f) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = fieldAlpha }
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
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { updated ->
                                textFieldValue = updated
                                onQueryChange(updated.text)
                            },
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
                                    if (textFieldValue.text.isNotEmpty()) {
                                        textFieldValue = TextFieldValue("", TextRange.Zero)
                                        onQueryChange("")
                                    } else {
                                        focusManager.clearFocus()
                                        onSearchClose()
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (textFieldValue.text.isNotEmpty()) "Clear" else "Close search",
                            tint = secondaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Viewer Smart Share FAB:
 * - Always occupies strictly 54dp x 54dp inside the dock Row (Popup floats independently above it,
 *   so the bottom bar NEVER moves vertically).
 * - Tap: opens native Android Sharesheet.
 * - Single-motion press + drag up + lift: opens recent-apps stack directly above FAB, highlights
 *   the app under the finger with haptic feedback, and fires direct share on finger release.
 */
@Composable
private fun SmartShareFab(
    medium: Medium,
    containerColor: Color,
    contentColor: Color,
    popupContainerColor: Color,
    popupContentColor: Color,
    border: BorderStroke,
    shadowElevation: Dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    var recentTargets by remember { mutableStateOf<List<QuickShareTarget>>(emptyList()) }
    val latestTargets by rememberUpdatedState(recentTargets)
    val latestMedium by rememberUpdatedState(medium)

    var isQuickMenuOpen by remember { mutableStateOf(false) }
    var hoveredIndex by remember { mutableIntStateOf(-1) }

    // Load only per MIME type (never keyed on isQuickMenuOpen so pointerInput is never cancelled mid-drag)
    LaunchedEffect(medium.mimeType) {
        recentTargets = RecentShareStore.loadRecentTargets(context, medium.mimeType)
    }

    LaunchedEffect(medium.id) {
        isQuickMenuOpen = false
        hoveredIndex = -1
    }

    val popupGapPx = with(density) { 10.dp.roundToPx() }
    val popupPadPx = with(density) { 6.dp.toPx() }
    val itemStridePx = with(density) { 48.dp.toPx() }
    val dragOpenThresholdPx = with(density) { 14.dp.toPx() }
    val maxHorizontalDriftPx = with(density) { 96.dp.toPx() }

    val popupPositionProvider = remember(popupGapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                // Align popup right edge with FAB right edge so label pills expand cleanly to the left
                val x = (anchorBounds.right - popupContentSize.width)
                    .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val y = (anchorBounds.top - popupContentSize.height - popupGapPx).coerceAtLeast(0)
                return IntOffset(x, y)
            }
        }
    }

    Box(
        modifier = Modifier.size(54.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isQuickMenuOpen && recentTargets.isNotEmpty()) {
            Popup(
                popupPositionProvider = popupPositionProvider,
                properties = PopupProperties(focusable = false, clippingEnabled = false)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Render top-to-bottom so index 0 (most recent) sits closest to the FAB / thumb
                    recentTargets.asReversed().forEachIndexed { visualIdx, target ->
                        val logicalIdx = recentTargets.lastIndex - visualIdx
                        val isHovered = logicalIdx == hoveredIndex
                        val itemScale by animateFloatAsState(
                            targetValue = if (isHovered) 1.18f else 1f,
                            animationSpec = LuminaMotion.responsiveSpring(),
                            label = "quick_share_scale_$logicalIdx"
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isHovered) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = popupContainerColor,
                                    border = border,
                                    shadowElevation = 4.dp
                                ) {
                                    Text(
                                        text = target.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = popupContentColor,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = if (isHovered) containerColor else popupContainerColor,
                                border = border,
                                shadowElevation = shadowElevation,
                                modifier = Modifier
                                    .size(46.dp)
                                    .graphicsLayer {
                                        scaleX = itemScale
                                        scaleY = itemScale
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        bitmap = target.icon,
                                        contentDescription = target.label,
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                    )
                                }
                            }
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
                .pointerInput(medium.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startY = down.position.y
                        var dragMenuActive = false
                        var movedPastSlop = false
                        var lastHovered = -1

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            val dyFromDown = change.position.y - startY

                            if (abs(dyFromDown) > viewConfiguration.touchSlop) {
                                movedPastSlop = true
                            }

                            val targets = latestTargets
                            if (!dragMenuActive && dyFromDown < -dragOpenThresholdPx && targets.isNotEmpty()) {
                                dragMenuActive = true
                                isQuickMenuOpen = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }

                            if (dragMenuActive) {
                                change.consume()
                                val yAboveFabTop = -change.position.y
                                val dxFromFabCenter = abs(change.position.x - size.width / 2f)
                                val distIntoStack = yAboveFabTop - popupGapPx - popupPadPx
                                val nextHover = if (
                                    dxFromFabCenter <= maxHorizontalDriftPx &&
                                    distIntoStack >= -itemStridePx * 0.25f &&
                                    distIntoStack <= targets.size * itemStridePx + itemStridePx * 0.35f
                                ) {
                                    (distIntoStack.coerceAtLeast(0f) / itemStridePx)
                                        .toInt()
                                        .coerceIn(0, targets.lastIndex)
                                } else {
                                    -1
                                }

                                if (nextHover != lastHovered) {
                                    lastHovered = nextHover
                                    hoveredIndex = nextHover
                                    if (nextHover != -1) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (dragMenuActive) {
                            val chosen = latestTargets.getOrNull(lastHovered)
                            isQuickMenuOpen = false
                            hoveredIndex = -1
                            if (chosen != null) {
                                RecentShareStore.shareDirectlyTo(context, latestMedium, chosen)
                            }
                        } else if (!movedPastSlop) {
                            RecentShareStore.launchNativeShareSheet(context, latestMedium)
                        }
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share (drag up for recent apps)",
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Unified expressive tab strip with a single sliding active indicator pill and continuous
 * float-driven label collapse (zero clipping of sibling tabs).
 */
@Composable
private fun HomeDockContent(
    currentTab: GalleryTab,
    isCollapsed: Boolean,
    onTabSelect: (GalleryTab) -> Unit
) {
    val density = LocalDensity.current
    val collapseProgress by animateFloatAsState(
        targetValue = if (isCollapsed) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = ExpressiveSpatialEasing),
        label = "dock_collapse_progress"
    )
    val selectedPosition by animateFloatAsState(
        targetValue = if (currentTab == GalleryTab.PHOTOS) 0f else 1f,
        animationSpec = tween(durationMillis = 200, easing = ExpressiveSpatialEasing),
        label = "dock_indicator_slide"
    )

    val padPx = with(density) { 5.dp.roundToPx() }
    val gapPx = with(density) { 4.dp.roundToPx() }
    val pillHeightPx = with(density) { 44.dp.roundToPx() }

    Layout(
        modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
        content = {
            // Child 0: Single sliding active indicator pill behind both tabs
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
            // Child 1: Photos tab
            SlidingDockTab(
                title = "Photos",
                icon = Icons.Default.PhotoSizeSelectActual,
                isSelected = currentTab == GalleryTab.PHOTOS,
                collapseProgress = collapseProgress,
                onClick = { onTabSelect(GalleryTab.PHOTOS) }
            )
            // Child 2: Albums tab
            SlidingDockTab(
                title = "Albums",
                icon = Icons.Default.PhotoLibrary,
                isSelected = currentTab == GalleryTab.ALBUMS,
                collapseProgress = collapseProgress,
                onClick = { onTabSelect(GalleryTab.ALBUMS) }
            )
        }
    ) { measurables, constraints ->
        val tabConstraints = Constraints(
            minWidth = 0,
            maxWidth = constraints.maxWidth,
            minHeight = pillHeightPx,
            maxHeight = pillHeightPx
        )
        val tab0 = measurables[1].measure(tabConstraints)
        val tab1 = measurables[2].measure(tabConstraints)

        val indW = lerp(tab0.width.toFloat(), tab1.width.toFloat(), selectedPosition)
            .roundToInt()
            .coerceAtLeast(1)
        val indX = lerp(0f, (tab0.width + gapPx).toFloat(), selectedPosition).roundToInt()
        val indicator = measurables[0].measure(Constraints.fixed(indW, pillHeightPx))

        val totalW = tab0.width + gapPx + tab1.width
        layout(totalW, pillHeightPx) {
            indicator.placeRelative(indX, 0)
            tab0.placeRelative(0, 0)
            tab1.placeRelative(tab0.width + gapPx, 0)
        }
    }
}

@Composable
private fun SlidingDockTab(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    collapseProgress: Float,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = LuminaMotion.colorTransition,
        label = "tab_content_color"
    )

    val horizontalPad = lerp(16f, 13f, collapseProgress).dp
    val iconTextGap = (7f * (1f - collapseProgress)).dp

    Row(
        modifier = Modifier
            .height(44.dp)
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = horizontalPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )

        // Smooth width + alpha label collapse without clipping sibling tabs
        Layout(
            modifier = Modifier.padding(start = iconTextGap),
            content = {
                Text(
                    text = title,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false
                )
            }
        ) { measurables, constraints ->
            val p = measurables[0].measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
            val currentW = (p.width * (1f - collapseProgress)).roundToInt().coerceAtLeast(0)
            layout(currentW, p.height) {
                val labelAlpha = (1f - collapseProgress * 1.8f).coerceIn(0f, 1f)
                if (labelAlpha > 0.01f) {
                    p.placeWithLayer(0, 0) { alpha = labelAlpha }
                }
            }
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
