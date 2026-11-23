package org.fossify.gallery.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.ui.theme.LuminaMotion
import org.fossify.gallery.ui.theme.pressScale

private enum class TopChromeMode {
    FLOATING_PILLS,
    SEARCH,
    SELECTION
}

@Composable
fun FloatingTopChrome(
    gridState: LazyGridState,
    indexToDatePill: List<String>,
    selectedTab: GalleryTab,
    isMultiSelectMode: Boolean,
    selectedCount: Int,
    isSearchActive: Boolean,
    searchQuery: String,
    activeAlbumName: String?,
    columnCount: Int,
    showConfirmPick: Boolean,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onAlbumBackClick: () -> Unit,
    onColumnCountChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onConfirmPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mode = when {
        isMultiSelectMode -> TopChromeMode.SELECTION
        isSearchActive -> TopChromeMode.SEARCH
        else -> TopChromeMode.FLOATING_PILLS
    }

    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            fadeIn(LuminaMotion.throughZeroEnter) togetherWith
                fadeOut(LuminaMotion.throughZeroExit)
        },
        label = "top_chrome_mode",
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) { target ->
        when (target) {
            TopChromeMode.SELECTION -> SelectionTopBar(
                selectedCount = selectedCount,
                showConfirmPick = showConfirmPick,
                onClearSelection = onClearSelection,
                onSelectAll = onSelectAll,
                onConfirmPick = onConfirmPick
            )

            TopChromeMode.SEARCH -> SearchBarOverlay(
                searchQuery = searchQuery,
                onQueryChange = onQueryChange,
                activeAlbumName = activeAlbumName,
                onAlbumBackClick = if (activeAlbumName != null) onAlbumBackClick else null,
                onCloseClick = onCloseSearch,
                autoFocus = true
            )

            TopChromeMode.FLOATING_PILLS -> FloatingDateAndMenuBar(
                gridState = gridState,
                indexToDatePill = indexToDatePill,
                selectedTab = selectedTab,
                activeAlbumName = activeAlbumName,
                columnCount = columnCount,
                onAlbumBackClick = onAlbumBackClick,
                onColumnCountChange = onColumnCountChange,
                onRefresh = onRefresh
            )
        }
    }
}

@Composable
private fun FloatingDateAndMenuBar(
    gridState: LazyGridState,
    indexToDatePill: List<String>,
    selectedTab: GalleryTab,
    activeAlbumName: String?,
    columnCount: Int,
    onAlbumBackClick: () -> Unit,
    onColumnCountChange: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    // High-contrast dark translucent pill matching full-bleed photo grid aesthetic
    val pillContainer = Color(0xFF141518).copy(alpha = 0.88f)
    val pillContent = Color(0xFFF3F4F6)

    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        // Left: Album back-chip only when drilled into an album
        if (activeAlbumName != null && selectedTab == GalleryTab.PHOTOS) {
            val backInteraction = remember { MutableInteractionSource() }
            Surface(
                shape = CircleShape,
                color = pillContainer,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .pressScale(backInteraction, pressedScale = 0.90f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = backInteraction,
                        indication = ripple(bounded = true),
                        role = Role.Button,
                        onClick = onAlbumBackClick
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to albums",
                        tint = pillContent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Center: Sticky floating date pill (or "Albums" / album title)
        // derivedStateOf is scoped strictly to this composable so scrolling never recomposes parent
        StickyCenterPill(
            gridState = gridState,
            indexToDatePill = indexToDatePill,
            selectedTab = selectedTab,
            activeAlbumName = activeAlbumName,
            containerColor = pillContainer,
            contentColor = pillContent,
            modifier = Modifier.align(Alignment.Center)
        )

        // Right: Floating circular overflow button (⋮)
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            val menuInteraction = remember { MutableInteractionSource() }
            Surface(
                shape = CircleShape,
                color = pillContainer,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(44.dp)
                    .pressScale(menuInteraction, pressedScale = 0.90f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = menuInteraction,
                        indication = ripple(bounded = true),
                        role = Role.Button,
                        onClick = { menuExpanded = true }
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Gallery options",
                        tint = pillContent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                for (cols in 2..5) {
                    DropdownMenuItem(
                        text = { Text("$cols columns") },
                        trailingIcon = if (columnCount == cols) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            menuExpanded = false
                            onColumnCountChange(cols)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Refresh library") },
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
private fun StickyCenterPill(
    gridState: LazyGridState,
    indexToDatePill: List<String>,
    selectedTab: GalleryTab,
    activeAlbumName: String?,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val label by remember(gridState, indexToDatePill, selectedTab, activeAlbumName) {
        derivedStateOf {
            when {
                selectedTab == GalleryTab.ALBUMS -> "Albums"
                indexToDatePill.isNotEmpty() -> {
                    val idx = gridState.firstVisibleItemIndex.coerceIn(0, indexToDatePill.lastIndex)
                    indexToDatePill[idx]
                }
                activeAlbumName != null -> activeAlbumName
                else -> ""
            }
        }
    }

    if (label.isNotEmpty()) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            shadowElevation = 4.dp,
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
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
