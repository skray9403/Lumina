package org.fossify.gallery.ui.viewer

import android.app.Activity
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.fossify.gallery.engine.video.PlayerPool
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.data.LuminaMediaRepository
import org.fossify.gallery.ui.details.LuminaDetailsBottomSheet
import org.fossify.gallery.ui.theme.LuminaMotion
import kotlin.math.max

/**
 * Full-screen immersive media viewer mounted over the live Home grid.
 * - Tile-bounds open/return morphing executed strictly in graphicsLayer.
 * - API 33+ PredictiveBackHandler scaling & revealing the live grid underneath.
 * - Vertical drag-to-dismiss sharing the exact same dismiss pipeline.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun LuminaViewer(
    mediaList: List<Medium>,
    initialIndex: Int,
    initialSourceRect: Rect?,
    viewModel: ViewerViewModel,
    repository: LuminaMediaRepository,
    onPageChanged: (Int) -> Unit,
    onPrepareReturnRect: suspend (Long) -> Rect?,
    onHiddenGridIdChange: (Long?) -> Unit,
    onDismissComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (mediaList.isEmpty()) {
        LaunchedEffect(Unit) { onDismissComplete() }
        return
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val safeInitial = initialIndex.coerceIn(0, mediaList.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeInitial,
        pageCount = { mediaList.size }
    )

    var canPagerSwipe by remember { mutableStateOf(true) }
    var isClosing by remember { mutableStateOf(false) }
    var activeTargetRect by remember { mutableStateOf(initialSourceRect) }

    // Layer-only animatables (zero recomposition per frame)
    val openProgress = remember { Animatable(0f) }
    val gestureScale = remember { Animatable(1f) }
    val gestureOffsetX = remember { Animatable(0f) }
    val gestureOffsetY = remember { Animatable(0f) }
    val backdropAlpha = remember { Animatable(1f) }
    val cornerRadiusDp = remember { Animatable(if (initialSourceRect != null) 4f else 0f) }

    // Open animation from clicked tile rect -> fullscreen
    LaunchedEffect(Unit) {
        val initialId = mediaList.getOrNull(safeInitial)?.id
        onHiddenGridIdChange(initialId)
        launch { cornerRadiusDp.animateTo(0f, LuminaMotion.viewerMorph) }
        openProgress.animateTo(1f, LuminaMotion.viewerMorph)
        onHiddenGridIdChange(null)
    }

    // Single hoisted PlayerPool with leak-free lifecycle observation
    val playerPool = remember(context) { PlayerPool(context) }
    DisposableEffect(playerPool, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                playerPool.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerPool.release()
            onHiddenGridIdChange(null)
        }
    }

    val actionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) {
        viewModel.clearPendingIntentRequest()
    }

    LaunchedEffect(state.pendingIntentRequest) {
        state.pendingIntentRequest?.let { req ->
            actionLauncher.launch(req)
        }
    }

    // Enforce light-on-dark system bar icons during viewer lifetime
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val prevLightStatus = controller?.isAppearanceLightStatusBars ?: false
        val prevLightNav = controller?.isAppearanceLightNavigationBars ?: false

        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        onDispose {
            controller?.apply {
                show(WindowInsetsCompat.Type.systemBars())
                isAppearanceLightStatusBars = prevLightStatus
                isAppearanceLightNavigationBars = prevLightNav
            }
        }
    }

    LaunchedEffect(state.isChromeVisible, activity) {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (state.isChromeVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val currentMedium = mediaList.getOrNull(pagerState.currentPage) ?: mediaList.first()

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setCurrentIndex(pagerState.currentPage)
        onPageChanged(pagerState.currentPage)
    }

    LaunchedEffect(currentMedium.id) {
        if (!currentMedium.isVideo) {
            playerPool.pause()
        }
    }

    fun triggerAnimatedClose() {
        if (isClosing) return
        isClosing = true
        playerPool.pause()
        scope.launch {
            val returnRect = onPrepareReturnRect(currentMedium.id)
            activeTargetRect = returnRect
            onHiddenGridIdChange(currentMedium.id)
            launch { backdropAlpha.animateTo(0f, LuminaMotion.viewerMorph) }
            launch { cornerRadiusDp.animateTo(if (returnRect != null) 6f else 18f, LuminaMotion.viewerMorph) }
            openProgress.animateTo(0f, LuminaMotion.viewerMorph)
            onHiddenGridIdChange(null)
            onDismissComplete()
        }
    }

    fun cancelInteractiveDismiss() {
        scope.launch {
            launch { gestureScale.animateTo(1f, LuminaMotion.responsiveSpring()) }
            launch { gestureOffsetX.animateTo(0f, LuminaMotion.responsiveSpring()) }
            launch { gestureOffsetY.animateTo(0f, LuminaMotion.responsiveSpring()) }
            launch { backdropAlpha.animateTo(1f, LuminaMotion.responsiveSpring()) }
            launch { cornerRadiusDp.animateTo(0f, LuminaMotion.responsiveSpring()) }
        }
    }

    // Native API 33+ Predictive Back Gesture
    val maxPullXPx = with(density) { 40.dp.toPx() }
    PredictiveBackHandler(enabled = !state.isDetailsSheetOpen && !isClosing) { progressFlow ->
        var initialTouchY = Float.NaN
        try {
            progressFlow.collect { event ->
                if (initialTouchY.isNaN()) initialTouchY = event.touchY
                val p = event.progress.coerceIn(0f, 1f)
                val edgeSign = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                gestureScale.snapTo(1f - 0.22f * p)
                gestureOffsetX.snapTo(edgeSign * maxPullXPx * p)
                gestureOffsetY.snapTo(((event.touchY - initialTouchY) * 0.45f).coerceIn(-140f, 140f))
                backdropAlpha.snapTo((1f - p * 1.15f).coerceIn(0f, 1f))
                cornerRadiusDp.snapTo(28f * p)
            }
            triggerAnimatedClose()
        } catch (_: CancellationException) {
            cancelInteractiveDismiss()
        }
    }

    val dismissThresholdPx = with(density) { 128.dp.toPx() }
    val showChrome = state.isChromeVisible && !isClosing && openProgress.value > 0.95f && backdropAlpha.value > 0.92f

    Box(
        modifier = modifier
            .fillMaxSize()
            // Draw-phase backdrop alpha reveals the live Home grid underneath during back/drag
            .drawBehind {
                val bgAlpha = (backdropAlpha.value * openProgress.value).coerceIn(0f, 1f)
                drawRect(Color.Black.copy(alpha = bgAlpha))
            }
    ) {
        // 1. Pager with layer-only open/close & predictive-back transform
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = canPagerSwipe && !isClosing,
            key = { index -> mediaList.getOrNull(index)?.id ?: index.toLong() },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = openProgress.value
                    val rect = activeTargetRect
                    val startScale = if (rect != null && size.width > 0f && size.height > 0f) {
                        max(rect.width / size.width, rect.height / size.height).coerceIn(0.05f, 1f)
                    } else {
                        0.90f
                    }
                    val finalScale = lerp(startScale, 1f, t) * gestureScale.value
                    val startTx = rect?.let { it.center.x - size.width / 2f } ?: 0f
                    val startTy = rect?.let { it.center.y - size.height / 2f } ?: 0f

                    scaleX = finalScale
                    scaleY = finalScale
                    translationX = lerp(startTx, gestureOffsetX.value, t)
                    translationY = lerp(startTy, gestureOffsetY.value, t)

                    val rDp = cornerRadiusDp.value
                    if (rDp > 0.5f) {
                        clip = true
                        shape = RoundedCornerShape(rDp.dp)
                    }
                    alpha = if (rect == null) t else (t * 4f).coerceIn(0f, 1f)
                }
        ) { page ->
            val medium = mediaList.getOrNull(page) ?: return@HorizontalPager
            val isCurrentPage = page == pagerState.currentPage

            if (medium.isVideo) {
                VideoPage(
                    medium = medium,
                    isCurrentPage = isCurrentPage && !isClosing,
                    playerPool = playerPool,
                    onSingleTap = { viewModel.toggleChrome() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PhotoPage(
                    medium = medium,
                    onSingleTap = { viewModel.toggleChrome() },
                    onEdgeSwipeHandoff = { canPagerSwipe = it },
                    onVerticalDismissDrag = { dragY ->
                        if (!isClosing) {
                            val clampedY = dragY.coerceAtLeast(0f)
                            val p = (clampedY / (dismissThresholdPx * 2.2f)).coerceIn(0f, 1f)
                            scope.launch {
                                gestureOffsetY.snapTo(clampedY * 0.85f)
                                gestureScale.snapTo(1f - 0.24f * p)
                                backdropAlpha.snapTo((1f - p * 1.15f).coerceIn(0f, 1f))
                                cornerRadiusDp.snapTo(24f * p)
                            }
                        }
                    },
                    onVerticalDismissEnd = { totalDragY ->
                        if (totalDragY >= dismissThresholdPx) {
                            triggerAnimatedClose()
                        } else {
                            cancelInteractiveDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. Gradient-backed top bar
        AnimatedVisibility(
            visible = showChrome,
            enter = slideInVertically(
                animationSpec = LuminaMotion.chromeOffset,
                initialOffsetY = { -it }
            ) + fadeIn(LuminaMotion.contentEnter),
            exit = slideOutVertically(
                animationSpec = LuminaMotion.chromeOffset,
                targetOffsetY = { -it }
            ) + fadeOut(LuminaMotion.contentExit),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ViewerTopBar(
                medium = currentMedium,
                onBack = { triggerAnimatedClose() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 3. Details Modal Bottom Sheet
        if (state.isDetailsSheetOpen) {
            LuminaDetailsBottomSheet(
                metadata = state.currentMetadata,
                isLoading = state.isLoadingMetadata,
                onDismiss = { viewModel.setDetailsSheetOpen(false) }
            )
        }
    }
}
