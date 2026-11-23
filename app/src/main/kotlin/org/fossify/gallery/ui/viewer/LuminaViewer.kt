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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import org.fossify.gallery.engine.image.NativeThumbnailLoader
import org.fossify.gallery.engine.video.PlayerPool
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.data.LuminaMediaRepository
import org.fossify.gallery.ui.details.LuminaDetailsBottomSheet
import org.fossify.gallery.ui.theme.LuminaMotion
import kotlin.math.roundToInt

private enum class MorphPhase {
    OPENING,
    IDLE,
    CLOSING
}

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
    onClosingStarted: () -> Unit,
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

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var canPagerSwipe by remember { mutableStateOf(true) }
    var morphPhase by remember { mutableStateOf(if (initialSourceRect != null) MorphPhase.OPENING else MorphPhase.IDLE) }

    val decodedBitmaps = remember { HashMap<Long, ImageBitmap>(4) }

    var morphStartRect by remember { mutableStateOf(initialSourceRect) }
    var morphEndRect by remember { mutableStateOf<Rect?>(null) }
    var morphStartCornerDp by remember { mutableStateOf(0f) }
    var morphEndCornerDp by remember { mutableStateOf(0f) }
    var morphBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val morphProgress = remember { Animatable(if (initialSourceRect != null) 0f else 1f) }
    val gestureScale = remember { Animatable(1f) }
    val gestureOffsetX = remember { Animatable(0f) }
    val gestureOffsetY = remember { Animatable(0f) }
    val backdropAlpha = remember { Animatable(if (initialSourceRect != null) 0f else 1f) }
    val gestureCornerDp = remember { Animatable(0f) }

    val playerPool = remember(context) { PlayerPool(context) }
    val activePlayer by playerPool.activePlayer.collectAsStateWithLifecycle()

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

    val currentMedium = mediaList.getOrNull(pagerState.currentPage) ?: mediaList.first()

    LaunchedEffect(viewportSize) {
        if (morphPhase == MorphPhase.OPENING && viewportSize.width > 0 && viewportSize.height > 0) {
            val initialMedium = mediaList.getOrNull(safeInitial) ?: mediaList.first()
            val bmp = decodedBitmaps[initialMedium.id] ?: NativeThumbnailLoader.getCached(initialMedium)
            if (initialSourceRect != null && bmp != null) {
                morphBitmap = bmp
                morphStartRect = initialSourceRect
                morphEndRect = computeFittedRect(viewportSize, initialMedium, bmp)
                morphStartCornerDp = 0f
                morphEndCornerDp = 0f
                onHiddenGridIdChange(initialMedium.id)
                launch { backdropAlpha.animateTo(1f, LuminaMotion.viewerMorph) }
                morphProgress.animateTo(1f, LuminaMotion.viewerMorph)
                onHiddenGridIdChange(null)
                morphPhase = MorphPhase.IDLE
            } else {
                backdropAlpha.snapTo(1f)
                morphProgress.snapTo(1f)
                morphPhase = MorphPhase.IDLE
            }
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
        if (morphPhase == MorphPhase.CLOSING) return
        morphPhase = MorphPhase.CLOSING
        onClosingStarted()
        playerPool.pause()
        scope.launch {
            val returnRect = onPrepareReturnRect(currentMedium.id)
            val bmp = decodedBitmaps[currentMedium.id] ?: NativeThumbnailLoader.getCached(currentMedium)
            if (returnRect != null && bmp != null && viewportSize.width > 0 && viewportSize.height > 0) {
                morphBitmap = bmp
                val baseFitted = computeFittedRect(viewportSize, currentMedium, bmp)
                val curScale = gestureScale.value
                val curW = baseFitted.width * curScale
                val curH = baseFitted.height * curScale
                val curCx = baseFitted.center.x + gestureOffsetX.value
                val curCy = baseFitted.center.y + gestureOffsetY.value
                morphStartRect = Rect(curCx - curW / 2f, curCy - curH / 2f, curCx + curW / 2f, curCy + curH / 2f)
                morphEndRect = returnRect
                morphStartCornerDp = gestureCornerDp.value
                morphEndCornerDp = 0f
                onHiddenGridIdChange(currentMedium.id)
                launch { backdropAlpha.animateTo(0f, LuminaMotion.viewerMorph) }
                morphProgress.snapTo(0f)
                morphProgress.animateTo(1f, LuminaMotion.viewerMorph)
                onHiddenGridIdChange(null)
            } else {
                launch { backdropAlpha.animateTo(0f, LuminaMotion.responsiveSpring()) }
                launch { gestureScale.animateTo(0.85f, LuminaMotion.responsiveSpring()) }
            }
            onDismissComplete()
        }
    }

    fun cancelInteractiveDismiss() {
        scope.launch {
            launch { gestureScale.animateTo(1f, LuminaMotion.responsiveSpring()) }
            launch { gestureOffsetX.animateTo(0f, LuminaMotion.responsiveSpring()) }
            launch { gestureOffsetY.animateTo(0f, LuminaMotion.responsiveSpring()) }
            launch { backdropAlpha.animateTo(1f, LuminaMotion.responsiveSpring()) }
            launch { gestureCornerDp.animateTo(0f, LuminaMotion.responsiveSpring()) }
        }
    }

    val maxPullXPx = with(density) { 48.dp.toPx() }
    PredictiveBackHandler(enabled = !state.isDetailsSheetOpen && morphPhase == MorphPhase.IDLE) { progressFlow ->
        var initialTouchY = Float.NaN
        try {
            progressFlow.collect { event ->
                if (initialTouchY.isNaN()) initialTouchY = event.touchY
                val p = event.progress.coerceIn(0f, 1f)
                val edgeSign = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                gestureScale.snapTo(1f - 0.18f * p)
                gestureOffsetX.snapTo(edgeSign * maxPullXPx * p)
                gestureOffsetY.snapTo(((event.touchY - initialTouchY) * 0.40f).coerceIn(-120f, 120f))
                backdropAlpha.snapTo((1f - p * 1.15f).coerceIn(0f, 1f))
                gestureCornerDp.snapTo(24f * p)
            }
            triggerAnimatedClose()
        } catch (_: CancellationException) {
            cancelInteractiveDismiss()
        }
    }

    val dismissThresholdPx = with(density) { 128.dp.toPx() }
    val showChrome = state.isChromeVisible && morphPhase == MorphPhase.IDLE && backdropAlpha.value > 0.92f

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .drawBehind {
                val bgAlpha = backdropAlpha.value.coerceIn(0f, 1f)
                drawRect(Color.Black.copy(alpha = bgAlpha))
            }
    ) {
        if (morphPhase == MorphPhase.IDLE) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = canPagerSwipe,
                key = { index -> mediaList.getOrNull(index)?.id ?: index.toLong() },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = gestureScale.value
                        scaleY = gestureScale.value
                        translationX = gestureOffsetX.value
                        translationY = gestureOffsetY.value
                        val r = gestureCornerDp.value
                        if (r > 0.5f) {
                            clip = true
                            shape = RoundedCornerShape(r.dp)
                        }
                    }
            ) { page ->
                val medium = mediaList.getOrNull(page) ?: return@HorizontalPager
                val isCurrentPage = page == pagerState.currentPage

                if (medium.isVideo) {
                    VideoPage(
                        medium = medium,
                        isCurrentPage = isCurrentPage,
                        playerPool = playerPool,
                        onSingleTap = { viewModel.toggleChrome() },
                        onVerticalDismissDrag = { dragY ->
                            val clampedY = dragY.coerceAtLeast(0f)
                            val p = (clampedY / (dismissThresholdPx * 2f)).coerceIn(0f, 1f)
                            scope.launch {
                                gestureOffsetY.snapTo(clampedY * 0.85f)
                                gestureScale.snapTo(1f - 0.20f * p)
                                backdropAlpha.snapTo((1f - p * 1.15f).coerceIn(0f, 1f))
                                gestureCornerDp.snapTo(24f * p)
                            }
                        },
                        onVerticalDismissEnd = { totalDragY ->
                            if (totalDragY >= dismissThresholdPx) {
                                triggerAnimatedClose()
                            } else {
                                cancelInteractiveDismiss()
                            }
                        },
                        onSwipeUpDetails = {
                            viewModel.loadMetadata(context, medium)
                            viewModel.setDetailsSheetOpen(true)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PhotoPage(
                        medium = medium,
                        onSingleTap = { viewModel.toggleChrome() },
                        onEdgeSwipeHandoff = { canPagerSwipe = it },
                        onVerticalDismissDrag = { dragY ->
                            val clampedY = dragY.coerceAtLeast(0f)
                            val p = (clampedY / (dismissThresholdPx * 2f)).coerceIn(0f, 1f)
                            scope.launch {
                                gestureOffsetY.snapTo(clampedY * 0.85f)
                                gestureScale.snapTo(1f - 0.20f * p)
                                backdropAlpha.snapTo((1f - p * 1.15f).coerceIn(0f, 1f))
                                gestureCornerDp.snapTo(24f * p)
                            }
                        },
                        onVerticalDismissEnd = { totalDragY ->
                            if (totalDragY >= dismissThresholdPx) {
                                triggerAnimatedClose()
                            } else {
                                cancelInteractiveDismiss()
                            }
                        },
                        onSwipeUpDetails = {
                            viewModel.loadMetadata(context, medium)
                            viewModel.setDetailsSheetOpen(true)
                        },
                        onBitmapReady = { id, bmp ->
                            decodedBitmaps[id] = bmp
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (morphPhase != MorphPhase.IDLE && morphStartRect != null && morphEndRect != null && morphBitmap != null) {
            val t = morphProgress.value
            val start = morphStartRect!!
            val end = morphEndRect!!
            val curRect = Rect(
                left = lerp(start.left, end.left, t),
                top = lerp(start.top, end.top, t),
                right = lerp(start.right, end.right, t),
                bottom = lerp(start.bottom, end.bottom, t)
            )
            val curCorner = lerp(morphStartCornerDp, morphEndCornerDp, t)
            val bmp = morphBitmap!!

            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            curRect,
                            CornerRadius(curCorner.dp.toPx(), curCorner.dp.toPx())
                        )
                    )
                }
                clipPath(path) {
                    drawImage(
                        image = bmp,
                        dstOffset = IntOffset(curRect.left.roundToInt(), curRect.top.roundToInt()),
                        dstSize = IntSize(curRect.width.roundToInt(), curRect.height.roundToInt())
                    )
                }
            }
        }

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
                isFavorite = currentMedium.isFavorite,
                onFavoriteToggle = { viewModel.toggleFavorite(repository, currentMedium) },
                onDetailsClick = {
                    viewModel.loadMetadata(context, currentMedium)
                    viewModel.setDetailsSheetOpen(true)
                },
                onBack = { triggerAnimatedClose() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (currentMedium.isVideo && activePlayer != null && showChrome) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 82.dp)
            ) {
                VideoTransportPill(player = activePlayer!!)
            }
        }

        if (state.isDetailsSheetOpen) {
            LuminaDetailsBottomSheet(
                metadata = state.currentMetadata,
                isLoading = state.isLoadingMetadata,
                onDismiss = { viewModel.setDetailsSheetOpen(false) }
            )
        }
    }
}

private fun computeFittedRect(viewport: IntSize, medium: Medium, bitmap: ImageBitmap?): Rect {
    val vw = viewport.width.toFloat()
    val vh = viewport.height.toFloat()
    if (vw <= 0f || vh <= 0f) return Rect(0f, 0f, vw, vh)
    val isQuarter = medium.orientation == 90 || medium.orientation == 270
    val rawW = if (isQuarter) medium.height else medium.width
    val rawH = if (isQuarter) medium.width else medium.height
    val mw = rawW.takeIf { it > 0 } ?: bitmap?.let { if (isQuarter) it.height else it.width } ?: vw.toInt()
    val mh = rawH.takeIf { it > 0 } ?: bitmap?.let { if (isQuarter) it.width else it.height } ?: vh.toInt()
    val aspect = mw.toFloat() / mh.toFloat().coerceAtLeast(1f)
    val vAspect = vw / vh
    val fw: Float
    val fh: Float
    if (aspect > vAspect) {
        fw = vw
        fh = vw / aspect
    } else {
        fh = vh
        fw = vh * aspect
    }
    val left = (vw - fw) / 2f
    val top = (vh - fh) / 2f
    return Rect(left, top, left + fw, top + fh)
}
