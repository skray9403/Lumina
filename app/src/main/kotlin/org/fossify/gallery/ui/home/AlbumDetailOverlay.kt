package org.fossify.gallery.ui.home

import android.app.Activity
import android.view.WindowManager
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.navigation.DockScrollState
import org.fossify.gallery.ui.theme.LuminaMotion

@Composable
fun AlbumDetailOverlay(
    albumName: String,
    isTrashOverlay: Boolean,
    isVaultOverlay: Boolean,
    media: List<Medium>,
    sections: List<TimelineSection>,
    indexToDatePill: List<String>,
    gridState: LazyGridState,
    dockScrollState: DockScrollState,
    columnCount: Int,
    mediaFilter: MediaFilter,
    groupBy: MediaGroupBy,
    sortOrder: SortOrder,
    selectedIds: Set<Long>,
    isMultiSelectMode: Boolean,
    hiddenMediaId: Long?,
    onGridRootOffsetChange: (Offset) -> Unit,
    enabledBack: Boolean,
    onMediaClick: (Medium) -> Unit,
    onMediaLongClick: (Medium) -> Unit,
    onSelectionChange: (Set<Long>) -> Unit,
    onColumnCountChange: (Int) -> Unit,
    onMediaFilterChange: (MediaFilter) -> Unit,
    onGroupByChange: (MediaGroupBy) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onRefresh: () -> Unit,
    onCloseComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Block screenshots, screen recording, and Recents thumbnails while Locked Folder is open
    DisposableEffect(isVaultOverlay, activity) {
        if (isVaultOverlay) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (isVaultOverlay) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    val enterProgress = remember { Animatable(0f) }
    val backScale = remember { Animatable(1f) }
    val backOffsetX = remember { Animatable(0f) }
    val backCornerDp = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enterProgress.animateTo(1f, LuminaMotion.responsiveSpring())
    }

    fun animateClose() {
        if (isDismissing) return
        isDismissing = true
        scope.launch {
            enterProgress.animateTo(0f, LuminaMotion.responsiveSpring())
            onCloseComplete()
        }
    }

    val maxPullXPx = with(density) { 48.dp.toPx() }
    val enterSlidePx = with(density) { 28.dp.toPx() }

    PredictiveBackHandler(enabled = enabledBack && !isDismissing) { progressFlow ->
        try {
            progressFlow.collect { event ->
                val p = event.progress.coerceIn(0f, 1f)
                val sign = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                backScale.snapTo(1f - 0.10f * p)
                backOffsetX.snapTo(sign * maxPullXPx * p)
                backCornerDp.snapTo(24f * p)
            }
            animateClose()
        } catch (_: CancellationException) {
            scope.launch {
                launch { backScale.animateTo(1f, LuminaMotion.responsiveSpring()) }
                launch { backOffsetX.animateTo(0f, LuminaMotion.responsiveSpring()) }
                launch { backCornerDp.animateTo(0f, LuminaMotion.responsiveSpring()) }
            }
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gridContentPadding = PaddingValues(
        top = statusBarTop + 62.dp,
        bottom = navBarBottom + 96.dp
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val t = enterProgress.value
                val s = lerp(0.93f, 1f, t) * backScale.value
                scaleX = s
                scaleY = s
                translationX = lerp(enterSlidePx, backOffsetX.value, t)
                alpha = t.coerceIn(0f, 1f)
                val r = backCornerDp.value
                if (r > 0.5f) {
                    clip = true
                    shape = RoundedCornerShape(r.dp)
                }
            }
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (media.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(gridContentPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isVaultOverlay -> Icons.Default.Lock
                            isTrashOverlay -> Icons.Default.DeleteOutline
                            albumName == "Favorites" -> Icons.Default.FavoriteBorder
                            else -> Icons.Default.PhotoLibrary
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when {
                            isVaultOverlay -> "Locked Folder is empty"
                            isTrashOverlay -> "Trash is empty"
                            albumName == "Favorites" -> "No favorites yet"
                            else -> "No items in $albumName"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            MediaTimelineGrid(
                media = media,
                sections = sections,
                showSectionHeaders = groupBy == MediaGroupBy.SIZE,
                indexToDatePill = indexToDatePill,
                columnCount = columnCount,
                gridState = gridState,
                selectedIds = selectedIds,
                isMultiSelectMode = isMultiSelectMode,
                hiddenMediaId = hiddenMediaId,
                onGridRootOffsetChange = onGridRootOffsetChange,
                onColumnCountChange = onColumnCountChange,
                onMediaClick = onMediaClick,
                onMediaLongClick = onMediaLongClick,
                onSelectionChange = onSelectionChange,
                contentPadding = gridContentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(dockScrollState.nestedScrollConnection)
            )
        }

        FloatingTopChrome(
            gridState = gridState,
            indexToDatePill = indexToDatePill,
            headerTitle = albumName,
            staticFloatingTitle = albumName,
            statusBarHeight = statusBarTop,
            alwaysFloating = true,
            columnCount = columnCount,
            mediaFilter = mediaFilter,
            groupBy = if (groupBy == MediaGroupBy.FOLDER) MediaGroupBy.DATE else groupBy,
            sortOrder = sortOrder,
            allowFolderGrouping = false,
            onBackClick = { animateClose() },
            onColumnCountChange = onColumnCountChange,
            onMediaFilterChange = onMediaFilterChange,
            onGroupByChange = onGroupByChange,
            onSortOrderChange = onSortOrderChange,
            onRefresh = onRefresh,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
