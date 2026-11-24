package org.fossify.gallery.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoSizeSelectActual
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
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

private val DockHeight = 58.dp
private val InnerPillHeight = 48.dp
private val SlotGap = 10.dp

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

    val hiddenOffsetPx = with(density) { 128.dp.toPx() }
    val slideProgress by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = LuminaMotion.chromeSlide,
        label = "dock_visibility_slide"
    )

    val searchProgress by animateFloatAsState(
        targetValue = if (isSearchExpanded) 1f else 0f,
        animationSpec = LuminaMotion.expressiveSpatial(),
        label = "dock_search_morph"
    )

    val containerColor by animateColorAsState(
        targetValue = pillTokens.containerColor,
        animationSpec = LuminaMotion.noBounceColor(),
        label = "dock_container_color"
    )

    val barHeightPx = with(density) { DockHeight.roundToPx() }
    val slotGapPx = with(density) { SlotGap.roundToPx() }
    val hasTrailingSlot = mode is DockMode.Home || mode is DockMode.Viewer

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .graphicsLayer {
                translationY = slideProgress * hiddenOffsetPx
                alpha = (1f - slideProgress * 1.15f).coerceIn(0f, 1f)
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Independent offset-gated Scroll-to-Top bubble floating 10dp above dock (Δy = 0)
        AnimatedVisibility(
            visible = homeMode != null && homeMode.showScrollToTop && !isSearchExpanded,
            enter = fadeIn(tween(120)) +
                scaleIn(LuminaMotion.expressiveBouncy(), initialScale = 0.72f) +
                slideInVertically(LuminaMotion.expressiveSpatial()) { it / 2 },
            exit = fadeOut(tween(90)) +
                scaleOut(tween(100), targetScale = 0.75f) +
                slideOutVertically(tween(100)) { it / 2 }
        ) {
            val topInteraction = remember { MutableInteractionSource() }
            Box(modifier = Modifier.padding(bottom = 10.dp)) {
                Surface(
                    shape = CircleShape,
                    color = containerColor,
                    contentColor = pillTokens.contentColor,
                    border = pillTokens.border,
                    shadowElevation = 5.dp,
                    modifier = Modifier
                        .size(42.dp)
                        .pressScale(topInteraction, pressedScale = 0.88f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = topInteraction,
                                indication = ripple(bounded = true),
                                role = Role.Button,
                                onClick = { homeMode?.onScrollToTop?.invoke() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Scroll to top",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Persistent 2-slot expressive morphing cluster
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                // Child 0: Primary morphing capsule
                Surface(
                    shape = CircleShape,
                    color = containerColor,
                    border = pillTokens.border,
                    shadowElevation = pillTokens.shadowElevation,
                    modifier = Modifier.height(DockHeight)
                ) {
                    AnimatedContent(
                        targetState = mode,
                        contentKey = { it::class },
                        transitionSpec = {
                            (fadeIn(LuminaMotion.throughZeroEnter) +
                                scaleIn(LuminaMotion.throughZeroEnter, initialScale = 0.94f)) togetherWith
                                (fadeOut(LuminaMotion.throughZeroExit) +
                                    scaleOut(LuminaMotion.throughZeroExit, targetScale = 0.94f)) using
                                SizeTransform(clip = false) { _, _ -> LuminaMotion.dockSizeSpec }
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
                                darkScheme = darkDynamicScheme,
                                onAddToAlbumClick = targetMode.onAddToAlbumClick,
                                onDeleteClick = targetMode.onDeleteClick,
                                context = context
                            )
                        }
                    }
                }

                // Child 1: Trailing detached pill (Dedicated Search morph in Home; Expressive FAB Menu Share in Viewer)
                if (hasTrailingSlot) {
                    when (mode) {
                        is DockMode.Home -> MorphingSearchPill(
                            searchProgress = searchProgress,
                            isSearchActive = mode.isSearchActive,
                            query = mode.searchQuery,
                            placeholder = if (mode.activeAlbumName != null) {
                                "Search in ${mode.activeAlbumName}..."
                            } else {
                                "Search photos, videos, albums..."
                            },
                            expandedContainerColor = containerColor,
                            contentColor = pillTokens.contentColor,
                            secondaryColor = pillTokens.secondaryContentColor,
                            border = pillTokens.border,
                            shadowElevation = pillTokens.shadowElevation,
                            onSearchOpen = mode.onSearchOpen,
                            onSearchClose = mode.onSearchClose,
                            onQueryChange = mode.onSearchQueryChange
                        )

                        is DockMode.Viewer -> ExpressiveSmartShareFab(
                            medium = mode.medium,
                            darkScheme = darkDynamicScheme,
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
 * Dedicated right Search FAB (secondaryContainer tonal contrast) smoothly morphing
 * via expressive spring into a full-width bottom Search field with synchronous cursor handling.
 */
@Composable
private fun MorphingSearchPill(
    searchProgress: Float,
    isSearchActive: Boolean,
    query: String,
    placeholder: String,
    expandedContainerColor: Color,
    contentColor: Color,
    secondaryColor: Color,
    border: BorderStroke,
    shadowElevation: Dp,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChange: (String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

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

    val bg by animateColorAsState(
        targetValue = if (isSearchActive) expandedContainerColor else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = LuminaMotion.noBounceColor(),
        label = "search_pill_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isSearchActive) secondaryColor else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = LuminaMotion.noBounceColor(),
        label = "search_pill_tint"
    )

    Surface(
        shape = CircleShape,
        color = bg,
        border = if (isSearchActive) border else null,
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
                .clip(CircleShape)
                .then(
                    if (!isSearchActive) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true),
                            role = Role.Button,
                            onClick = onSearchOpen
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val fabAlpha = (1f - searchProgress * 3.0f).coerceIn(0f, 1f)
            if (fabAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = fabAlpha },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = iconTint,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            val fieldAlpha = ((searchProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
            if (fieldAlpha > 0.01f) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = fieldAlpha }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = secondaryColor,
                        modifier = Modifier.size(21.dp)
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
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Material 3 Expressive FAB Menu Share button:
 * - Normal tap: launches native Android Sharesheet.
 * - Single-motion press + drag up + release: cascades staggered Material 3 Expressive
 *   full-capsule [Icon + App Name] pills right above the FAB while morphing the FAB to the
 *   circular '×' state; sliding over any pill highlights it with haptic feedback and executes
 *   direct share on finger lift. Zero layout impact on the bottom bar.
 */
@Composable
private fun ExpressiveSmartShareFab(
    medium: Medium,
    darkScheme: ColorScheme,
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

    LaunchedEffect(medium.mimeType) {
        recentTargets = RecentShareStore.loadRecentTargets(context, medium.mimeType)
    }

    LaunchedEffect(medium.id) {
        isQuickMenuOpen = false
        hoveredIndex = -1
    }

    val popupGapPx = with(density) { 12.dp.roundToPx() }
    val itemHeightDp = 52.dp
    val itemGapDp = 8.dp
    val itemStridePx = with(density) { (itemHeightDp + itemGapDp).toPx() }
    val dragOpenThresholdPx = with(density) { 14.dp.toPx() }
    val maxLeftDriftPx = with(density) { 230.dp.toPx() }
    val maxRightDriftPx = with(density) { 44.dp.toPx() }

    val popupPositionProvider = remember(popupGapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = (anchorBounds.right - popupContentSize.width)
                    .coerceIn(12, (windowSize.width - popupContentSize.width).coerceAtLeast(12))
                val y = (anchorBounds.top - popupContentSize.height - popupGapPx).coerceAtLeast(0)
                return IntOffset(x, y)
            }
        }
    }

    val fabBg by animateColorAsState(
        targetValue = if (isQuickMenuOpen) darkScheme.primary else darkScheme.primaryContainer,
        animationSpec = LuminaMotion.noBounceColor(),
        label = "share_fab_bg"
    )
    val fabTint by animateColorAsState(
        targetValue = if (isQuickMenuOpen) darkScheme.onPrimary else darkScheme.onPrimaryContainer,
        animationSpec = LuminaMotion.noBounceColor(),
        label = "share_fab_tint"
    )
    val fabRotation by animateFloatAsState(
        targetValue = if (isQuickMenuOpen) 90f else 0f,
        animationSpec = LuminaMotion.expressiveBouncy(),
        label = "share_fab_rot"
    )

    Box(
        modifier = Modifier.size(DockHeight),
        contentAlignment = Alignment.Center
    ) {
        if (isQuickMenuOpen && recentTargets.isNotEmpty()) {
            Popup(
                popupPositionProvider = popupPositionProvider,
                properties = PopupProperties(focusable = false, clippingEnabled = false)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(itemGapDp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Index 0 sits closest to thumb at bottom; cascade staggers bottom -> top
                    recentTargets.asReversed().forEachIndexed { visualIdx, target ->
                        val logicalIdx = recentTargets.lastIndex - visualIdx
                        val isHovered = logicalIdx == hoveredIndex
                        ExpressiveShareMenuItemPill(
                            target = target,
                            staggerIndex = logicalIdx,
                            isHovered = isHovered,
                            darkScheme = darkScheme,
                            height = itemHeightDp
                        )
                    }
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = fabBg,
            border = if (isQuickMenuOpen) null else border,
            shadowElevation = shadowElevation,
            modifier = Modifier
                .size(DockHeight)
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
                                val posX = change.position.x
                                val inHorizontalBand = posX >= -maxLeftDriftPx && posX <= size.width + maxRightDriftPx
                                val distIntoStack = yAboveFabTop - popupGapPx
                                val nextHover = if (
                                    inHorizontalBand &&
                                    distIntoStack >= -itemStridePx * 0.2f &&
                                    distIntoStack <= targets.size * itemStridePx + itemStridePx * 0.3f
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.graphicsLayer { rotationZ = fabRotation }
            ) {
                Icon(
                    imageVector = if (isQuickMenuOpen) Icons.Default.Close else Icons.Default.Share,
                    contentDescription = "Share (drag up for recent apps)",
                    tint = fabTint,
                    modifier = Modifier.size(23.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpressiveShareMenuItemPill(
    target: QuickShareTarget,
    staggerIndex: Int,
    isHovered: Boolean,
    darkScheme: ColorScheme,
    height: Dp
) {
    val density = LocalDensity.current
    val appear = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(staggerIndex * 26L)
        appear.animateTo(1f, LuminaMotion.expressiveBouncy())
    }

    val hoverScale by animateFloatAsState(
        targetValue = if (isHovered) 1.06f else 1f,
        animationSpec = LuminaMotion.expressiveBouncy(),
        label = "share_pill_hover_scale"
    )
    val pillBg by animateColorAsState(
        targetValue = if (isHovered) darkScheme.primary else darkScheme.primaryContainer,
        animationSpec = LuminaMotion.noBounceColor(),
        label = "share_pill_bg"
    )
    val pillFg by animateColorAsState(
        targetValue = if (isHovered) darkScheme.onPrimary else darkScheme.onPrimaryContainer,
        animationSpec = LuminaMotion.noBounceColor(),
        label = "share_pill_fg"
    )
    val enterOffsetPx = with(density) { 18.dp.toPx() }

    Surface(
        shape = CircleShape,
        color = pillBg,
        contentColor = pillFg,
        shadowElevation = if (isHovered) 10.dp else 6.dp,
        modifier = Modifier
            .height(height)
            .graphicsLayer {
                val p = appear.value
                val s = lerp(0.80f, 1f, p) * hoverScale
                scaleX = s
                scaleY = s
                translationY = (1f - p) * enterOffsetPx
                alpha = p.coerceIn(0f, 1f)
            }
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                bitmap = target.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
            )
            Text(
                text = target.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = pillFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Substantial 58dp expressive navigation dock with spring-driven sliding active indicator,
 * active icon scale pop, and smooth spring scroll collapse.
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
        animationSpec = LuminaMotion.expressiveSpatial(),
        label = "dock_collapse_progress"
    )
    val selectedPosition by animateFloatAsState(
        targetValue = if (currentTab == GalleryTab.PHOTOS) 0f else 1f,
        animationSpec = LuminaMotion.expressiveSpatial(),
        label = "dock_indicator_slide"
    )

    val gapPx = with(density) { 4.dp.roundToPx() }
    val pillHeightPx = with(density) { InnerPillHeight.roundToPx() }

    Layout(
        modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
        content = {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
            SlidingDockTab(
                title = "Photos",
                icon = Icons.Default.PhotoSizeSelectActual,
                isSelected = currentTab == GalleryTab.PHOTOS,
                collapseProgress = collapseProgress,
                onClick = { onTabSelect(GalleryTab.PHOTOS) }
            )
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
        animationSpec = LuminaMotion.noBounceColor(),
        label = "tab_content_color"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = LuminaMotion.expressiveBouncy(),
        label = "tab_icon_pop"
    )

    val horizontalPad = lerp(18f, 15f, collapseProgress).coerceAtLeast(0f).dp
    val iconTextGap = (8f * (1f - collapseProgress)).coerceAtLeast(0f).dp

    Row(
        modifier = Modifier
            .height(InnerPillHeight)
            .pressScale(interactionSource, pressedScale = 0.93f)
            .clip(CircleShape)
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
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )

        Layout(
            modifier = Modifier.padding(start = iconTextGap),
            content = {
                Text(
                    text = title,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        ) { measurables, constraints ->
            val p = measurables[0].measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
            val currentW = (p.width * (1f - collapseProgress)).roundToInt().coerceAtLeast(0)
            layout(currentW, p.height) {
                val labelAlpha = (1f - collapseProgress * 1.7f).coerceIn(0f, 1f)
                if (labelAlpha > 0.01f) {
                    p.placeWithLayer(0, 0) { alpha = labelAlpha }
                }
            }
        }
    }
}

/**
 * Connected Material 3 Expressive 3-pill selection bar with distinct tonal containers.
 */
@Composable
private fun SelectionDockContent(
    onShare: () -> Unit,
    onAddToAlbum: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConnectedActionPill(
            icon = Icons.Default.Share,
            label = null,
            contentDescription = "Share selected",
            containerColor = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurface,
            outerStartRadius = 24.dp,
            outerEndRadius = 10.dp,
            onClick = onShare
        )
        ConnectedActionPill(
            icon = Icons.Default.Add,
            label = "Add to",
            contentDescription = "Add selected to album",
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
            outerStartRadius = 10.dp,
            outerEndRadius = 10.dp,
            onClick = onAddToAlbum
        )
        ConnectedActionPill(
            icon = Icons.Default.Delete,
            label = null,
            contentDescription = "Delete selected",
            containerColor = scheme.errorContainer.copy(alpha = 0.72f),
            contentColor = scheme.onErrorContainer,
            outerStartRadius = 10.dp,
            outerEndRadius = 24.dp,
            onClick = onDelete
        )
    }
}

/**
 * Connected Material 3 Expressive Viewer bar:
 * 3 visually distinct tonal capsules with asymmetric outer/inner corner radii that morph on press.
 * [ Edit (neutral tonal) | + Add to (secondaryContainer hero) | Delete (error-tinted) ]
 */
@Composable
private fun ViewerDockContent(
    medium: Medium,
    darkScheme: ColorScheme,
    onAddToAlbumClick: () -> Unit,
    onDeleteClick: () -> Unit,
    context: Context
) {
    Row(
        modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConnectedActionPill(
            icon = Icons.Default.Edit,
            label = null,
            contentDescription = "Edit",
            containerColor = darkScheme.surfaceContainerHighest,
            contentColor = darkScheme.onSurface,
            outerStartRadius = 24.dp,
            outerEndRadius = 10.dp,
            onClick = {
                val editIntent = Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(medium.uri, medium.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(editIntent, "Edit with"))
            }
        )
        ConnectedActionPill(
            icon = Icons.Default.Add,
            label = "Add to",
            contentDescription = "Add to album",
            containerColor = darkScheme.secondaryContainer,
            contentColor = darkScheme.onSecondaryContainer,
            outerStartRadius = 10.dp,
            outerEndRadius = 10.dp,
            onClick = onAddToAlbumClick
        )
        ConnectedActionPill(
            icon = Icons.Default.Delete,
            label = null,
            contentDescription = "Delete",
            containerColor = darkScheme.errorContainer.copy(alpha = 0.55f),
            contentColor = darkScheme.onErrorContainer,
            outerStartRadius = 10.dp,
            outerEndRadius = 24.dp,
            onClick = onDeleteClick
        )
    }
}

@Composable
private fun ConnectedActionPill(
    icon: ImageVector,
    label: String?,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    outerStartRadius: Dp,
    outerEndRadius: Dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Expressive corner-radius morph on touch-down (connected inner 10dp softens to 18dp)
    val startCorner by animateDpAsState(
        targetValue = if (isPressed) 18.dp else outerStartRadius,
        animationSpec = LuminaMotion.expressiveSnappy(),
        label = "connected_start_corner"
    )
    val endCorner by animateDpAsState(
        targetValue = if (isPressed) 18.dp else outerEndRadius,
        animationSpec = LuminaMotion.expressiveSnappy(),
        label = "connected_end_corner"
    )
    val shape = RoundedCornerShape(
        topStart = startCorner.coerceAtLeast(0.dp),
        bottomStart = startCorner.coerceAtLeast(0.dp),
        topEnd = endCorner.coerceAtLeast(0.dp),
        bottomEnd = endCorner.coerceAtLeast(0.dp)
    )

    Box(
        modifier = Modifier
            .height(InnerPillHeight)
            .then(
                if (label == null) Modifier.width(56.dp) else Modifier.widthIn(min = 104.dp)
            )
            .pressScale(interactionSource, pressedScale = 0.92f)
            .clip(shape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = if (label == null) 0.dp else 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(if (label == null) 21.dp else 20.dp)
            )
            if (label != null) {
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
