package org.fossify.gallery.ui.home

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.navigation.DockScrollState
import org.fossify.gallery.ui.theme.BottomEdgeFeatherFade
import org.fossify.gallery.ui.theme.LuminaMotion
import org.fossify.gallery.ui.theme.TopEdgeFeatherFade

@Composable
fun AlbumDetailOverlay(
    albumName: String,
    media: List<Medium>,
    indexToDatePill: List<String>,
    gridState: LazyGridState,
    dockScrollState: DockScrollState,
    columnCount: Int,
    selectedIds: Set<Long>,
    isMultiSelectMode: Boolean,
    hiddenMediaId: Long?,
    tileBounds: MutableMap<Long, Rect>,
    showConfirmPick: Boolean,
    enabledBack: Boolean,
    onMediaClick: (Medium, Rect?) -> Unit,
    onMediaLongClick: (Medium) -> Unit,
    onColumnCountChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onConfirmPick: () -> Unit,
    onCloseComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

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
        MediaTimelineGrid(
            media = media,
            columnCount = columnCount,
            gridState = gridState,
            selectedIds = selectedIds,
            isMultiSelectMode = isMultiSelectMode,
            hiddenMediaId = hiddenMediaId,
            tileBounds = tileBounds,
            onMediaClick = onMediaClick,
            onMediaLongClick = onMediaLongClick,
            contentPadding = gridContentPadding,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(dockScrollState.nestedScrollConnection)
        )

        TopEdgeFeatherFade(
            height = statusBarTop + 68.dp,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        BottomEdgeFeatherFade(
            height = navBarBottom + 98.dp,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        FloatingTopChrome(
            gridState = gridState,
            indexToDatePill = indexToDatePill,
            staticTitle = albumName,
            isMultiSelectMode = isMultiSelectMode,
            selectedCount = selectedIds.size,
            columnCount = columnCount,
            showConfirmPick = showConfirmPick,
            onBackClick = { animateClose() },
            onColumnCountChange = onColumnCountChange,
            onRefresh = onRefresh,
            onClearSelection = onClearSelection,
            onSelectAll = onSelectAll,
            onConfirmPick = onConfirmPick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}
