package org.fossify.gallery.ui.viewer

import android.app.Activity
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
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
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fossify.gallery.engine.image.NativeThumbnailLoader
import org.fossify.gallery.engine.vault.VaultManager
import org.fossify.gallery.engine.video.PlayerPool
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.data.LuminaMediaRepository
import org.fossify.gallery.ui.details.LuminaDetailsBottomSheet
import org.fossify.gallery.ui.theme.LuminaMotion
import kotlin.math.max
import kotlin.math.roundToInt

private enum class MorphPhase { OPENING, IDLE, CLOSING }

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun LuminaViewer(
    mediaList: List<Medium>,
    initialIndex: Int,
    initialSourceRect: Rect?,
    viewModel: ViewerViewModel,
    repository: LuminaMediaRepository,
    useLightChrome: Boolean = false,
    isShareMenuOpen: Boolean = false,
    isTrashMode: Boolean = false,
    onHideItem: ((Medium) -> Unit)? = null,
    onPageChanged: (Int) -> Unit,
    onPrepareReturnRect: suspend (Long) -> Rect?,
    onHiddenGridIdChange: (Long?) -> Unit,
    onVideoActionDockVisibleChange: (Boolean) -> Unit,
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
    val pagerState = rememberPagerState(initialPage = safeInitial, pageCount = { mediaList.size })

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var canPagerSwipe by remember { mutableStateOf(true) }
    var morphPhase by remember { mutableStateOf(if (initialSourceRect != null) MorphPhase.OPENING else MorphPhase.IDLE) }

    val decodedBitmaps = remember { HashMap<Long, ImageBitmap>(6) }
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

    var isVideoPlaying by remember { mutableStateOf(false) }
    var isVideoScrubbing by remember { mutableStateOf(false) }
    var videoActionDockVisible by remember { mutableStateOf(true) }

    DisposableEffect(activePlayer) {
        val player = activePlayer
        if (player == null) {
            isVideoPlaying = false
            return@DisposableEffect onDispose {}
        }
        isVideoPlaying = player.isPlaying || player.playWhenReady
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isVideoPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) isVideoPlaying = false
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(playerPool, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) playerPool.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerPool.release()
            onHiddenGridIdChange(null)
            onVideoActionDockVisibleChange(true)
        }
    }

    val currentMedium = mediaList.getOrNull(pagerState.currentPage) ?: mediaList.first()
    val isCurrentVaultItem = VaultManager.isVaultMedium(currentMedium)

    // Light canvas & light chrome only when Light Mode + bright photo + chrome visible (Apple/Google Photos behavior)
    val effectiveLightChrome = useLightChrome && !currentMedium.isVideo && state.isChromeVisible
    val canvasBgColor by animateColorAsState(
        targetValue = if (effectiveLightChrome) MaterialTheme.colorScheme.background else Color.Black,
        animationSpec = LuminaMotion.noBounceColor(),
        label = "viewer_canvas_bg"
    )

    LaunchedEffect(
        currentMedium.id,
        currentMedium.isVideo,
        isVideoPlaying,
        isVideoScrubbing,
        isShareMenuOpen,
        state.isDetailsSheetOpen
    ) {
        if (!currentMedium.isVideo || !isVideoPlaying || isVideoScrubbing || isShareMenuOpen || state.isDetailsSheetOpen) {
            videoActionDockVisible = true
        } else {
            videoActionDockVisible = true
            delay(1600L)
            videoActionDockVisible = false
        }
    }

    LaunchedEffect(videoActionDockVisible) {
        onVideoActionDockVisibleChange(videoActionDockVisible)
    }

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
            } else {
                backdropAlpha.snapTo(1f)
                morphProgress.snapTo(1f)
            }
            morphPhase = MorphPhase.IDLE
        }
    }

    val actionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) {
        viewModel.clearPendingIntentRequest()
    }

    LaunchedEffect(state.pendingIntentRequest) {
        state.pendingIntentRequest?.let { actionLauncher.launch(it) }
    }

    // Save original bar icon appearance once & restore on viewer exit
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val prevLightStatus = controller?.isAppearanceLightStatusBars ?: false
        val prevLightNav = controller?.isAppearanceLightNavigationBars ?: false
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.apply {
                show(WindowInsetsCompat.Type.systemBars())
                isAppearanceLightStatusBars = prevLightStatus
                isAppearanceLightNavigationBars = prevLightNav
            }
        }
    }

    // Dynamically sync status/nav bar icon color & visibility with active photo luminance
    LaunchedEffect(state.isChromeVisible, effectiveLightChrome, activity) {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = effectiveLightChrome
            controller.isAppearanceLightNavigationBars = effectiveLightChrome
            if (state.isChromeVisible) controller.show(WindowInsetsCompat.Type.systemBars())
            else controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setCurrentIndex(pagerState.currentPage)
        onPageChanged(pagerState.currentPage)
    }

    LaunchedEffect(currentMedium.id) {
        if (!currentMedium.isVideo) playerPool.pause()
    }

    fun openDetailsSheet() {
        viewModel.loadMetadata(context, currentMedium)
        viewModel.setDetailsSheetOpen(true)
    }

    fun triggerAnimatedClose() {
        if (morphPhase == MorphPhase.CLOSING) return
        morphPhase = MorphPhase.CLOSING
        onClosingStarted()
        playerPool.pause()

        scope.launch {
            val targetTileRect = onPrepareReturnRect(currentMedium.id)
            val bmp = decodedBitmaps[currentMedium.id] ?: NativeThumbnailLoader.getCached(currentMedium)

            if (targetTileRect != null && bmp != null && viewportSize.width > 0 && viewportSize.height > 0) {
                val baseFitted = computeFittedRect(viewportSize, currentMedium, bmp)
                val curScale = gestureScale.value
                val curW = baseFitted.width * curScale
                val curH = baseFitted.height * curScale
                val curCx = viewportSize.width / 2f + gestureOffsetX.value
                val curCy = viewportSize.height / 2f + gestureOffsetY.value

                morphBitmap = bmp
                morphStartRect = Rect(curCx - curW / 2f, curCy - curH / 2f, curCx + curW / 2f, curCy + curH / 2f)
                morphEndRect = targetTileRect
                morphStartCornerDp = gestureCornerDp.value
                morphEndCornerDp = 0f
                onHiddenGridIdChange(currentMedium.id)

                morphProgress.snapTo(0f)
                launch { backdropAlpha.animateTo(0f, LuminaMotion.viewerMorph) }
                morphProgress.animateTo(1f, LuminaMotion.viewerMorph)
            } else {
                backdropAlpha.animateTo(0f, LuminaMotion.contentExit)
            }

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
            launch { gestureCornerDp.animateTo(0f, LuminaMotion.responsiveSpring()) }
        }
    }

    val maxPullXPx = with(density) { 42.dp.toPx() }
    PredictiveBackHandler(enabled = !state.isDetailsSheetOpen && morphPhase == MorphPhase.IDLE) { progressFlow ->
        var initialTouchY = Float.NaN
        try {
            progressFlow.collect { event ->
                if (initialTouchY.isNaN()) initialTouchY = event.touchY
                val p = event.progress.coerceIn(0f, 1f)
                val edgeSign = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                gestureScale.snapTo(1f - 0.22f * p)
                gestureOffsetX.snapTo(edgeSign * maxPullXPx * p)
                gestureOffsetY.snapTo(((event.touchY - initialTouchY) * 0.42f).coerceIn(-140f, 140f))
                backdropAlpha.snapTo((1f - p * 1.15f).coerceIn(0f, 1f))
                gestureCornerDp.snapTo(24f * p)
            }
            triggerAnimatedClose()
        } catch (_: CancellationException) {
            cancelInteractiveDismiss()
        }
    }

    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    val showChrome = state.isChromeVisible && morphPhase == MorphPhase.IDLE && backdropAlpha.value > 0.92f
    val clipPath = remember { Path() }
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val transportBottomPadding by animateDpAsState(
        targetValue = if (videoActionDockVisible) 84.dp else 12.dp,
        animationSpec = LuminaMotion.expressiveSpatial(),
        label = "video_transport_bottom_pad"
    )

    val bottomScrimHeight by animateDpAsState(
        targetValue = navBarBottom + when {
            currentMedium.isVideo && videoActionDockVisible -> 196.dp
            currentMedium.isVideo -> 124.dp
            else -> 108.dp
        },
        animationSpec = LuminaMotion.expressiveSpatial(),
        label = "viewer_bottom_scrim_height"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .drawBehind { drawRect(canvasBgColor.copy(alpha = backdropAlpha.value.coerceIn(0f, 1f))) }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = canPagerSwipe && morphPhase == MorphPhase.IDLE,
            key = { index -> mediaList.getOrNull(index)?.id ?: index.toLong() },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (morphPhase == MorphPhase.IDLE) 1f else 0f
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

            val onDrag: (Float) -> Unit = { dragY ->
                if (morphPhase == MorphPhase.IDLE) {
                    val clampedY = dragY.coerceAtLeast(0f)
                    val p = (clampedY / (dismissThresholdPx * 2.2f)).coerceIn(0f, 1f)
                    scope.launch {
                        gestureOffsetY.snapTo(clampedY * 0.85f)
                        gestureScale.snapTo(1f - 0.24f * p)
                        backdropAlpha.snapTo((1f - p * 1.15f).coerceIn(0f, 1f))
                        gestureCornerDp.snapTo(22f * p)
                    }
                }
            }
            val onDragEnd: (Float) -> Unit = { totalDragY ->
                if (totalDragY >= dismissThresholdPx) triggerAnimatedClose() else cancelInteractiveDismiss()
            }

            if (medium.isVideo) {
                VideoPage(
                    medium = medium,
                    isCurrentPage = isCurrentPage && morphPhase == MorphPhase.IDLE,
                    playerPool = playerPool,
                    onSingleTap = { viewModel.toggleChrome() },
                    onVerticalDismissDrag = onDrag,
                    onVerticalDismissEnd = onDragEnd,
                    onSwipeUpDetails = { openDetailsSheet() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PhotoPage(
                    medium = medium,
                    useLightChrome = effectiveLightChrome,
                    onSingleTap = { viewModel.toggleChrome() },
                    onEdgeSwipeHandoff = { canPagerSwipe = it },
                    onVerticalDismissDrag = onDrag,
                    onVerticalDismissEnd = onDragEnd,
                    onSwipeUpDetails = { openDetailsSheet() },
                    onBitmapReady = { id, bmp ->
                        if (decodedBitmaps.size > 6) decodedBitmaps.clear()
                        decodedBitmaps[id] = bmp
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (morphPhase != MorphPhase.IDLE) {
            val bmp = morphBitmap
            val sRect = morphStartRect
            val eRect = morphEndRect
            if (bmp != null && sRect != null && eRect != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val t = morphProgress.value.coerceIn(0f, 1f)
                    val rect = Rect(
                        left = lerp(sRect.left, eRect.left, t),
                        top = lerp(sRect.top, eRect.top, t),
                        right = lerp(sRect.right, eRect.right, t),
                        bottom = lerp(sRect.bottom, eRect.bottom, t)
                    )
                    val radiusPx = lerp(morphStartCornerDp, morphEndCornerDp, t).dp.toPx()

                    clipPath.reset()
                    clipPath.addRoundRect(RoundRect(rect, CornerRadius(radiusPx, radiusPx)))

                    val bw = bmp.width.toFloat().coerceAtLeast(1f)
                    val bh = bmp.height.toFloat().coerceAtLeast(1f)
                    val cropScale = max(rect.width / bw, rect.height / bh)
                    val drawW = bw * cropScale
                    val drawH = bh * cropScale
                    val drawLeft = rect.center.x - drawW / 2f
                    val drawTop = rect.center.y - drawH / 2f

                    clipPath(clipPath) {
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset(drawLeft.roundToInt(), drawTop.roundToInt()),
                            dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt())
                        )
                    }
                }
            }
        }

        // Adaptive bottom scrim anchored flush to 0.dp physical bottom edge (light in Light Mode on bright photos, dark otherwise)
        val bottomScrimBase = if (effectiveLightChrome) MaterialTheme.colorScheme.background else Color.Black
        val bottomScrimMidAlpha = if (effectiveLightChrome) 0.42f else 0.34f
        val bottomScrimEndAlpha = if (effectiveLightChrome) 0.78f else 0.84f

        AnimatedVisibility(
            visible = showChrome && !state.isDetailsSheetOpen,
            enter = fadeIn(LuminaMotion.contentEnter),
            exit = fadeOut(LuminaMotion.contentExit),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomScrimHeight.coerceAtLeast(64.dp))
                    .drawWithCache {
                        val scrim = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.35f to bottomScrimBase.copy(alpha = bottomScrimMidAlpha),
                            0.72f to bottomScrimBase.copy(alpha = bottomScrimEndAlpha * 0.82f),
                            1.0f to bottomScrimBase.copy(alpha = bottomScrimEndAlpha)
                        )
                        onDrawBehind { drawRect(brush = scrim) }
                    }
            )
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = slideInVertically(LuminaMotion.chromeOffset) { -it } + fadeIn(LuminaMotion.contentEnter),
            exit = slideOutVertically(LuminaMotion.chromeOffset) { -it } + fadeOut(LuminaMotion.contentExit),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ViewerTopBar(
                medium = currentMedium,
                isFavorite = currentMedium.isFavorite,
                isVaultMode = isCurrentVaultItem,
                useLightChrome = effectiveLightChrome,
                onFavoriteToggle = { viewModel.toggleFavorite(repository, currentMedium) },
                onDetailsClick = { openDetailsSheet() },
                onHideClick = if (!isCurrentVaultItem && !isTrashMode) {
                    onHideItem?.let { cb -> { cb(currentMedium) } }
                } else {
                    null
                },
                onBack = { triggerAnimatedClose() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        val transportPlayer = activePlayer
        AnimatedVisibility(
            visible = showChrome && currentMedium.isVideo && transportPlayer != null && !state.isDetailsSheetOpen,
            enter = slideInVertically(LuminaMotion.chromeOffset) { it / 2 } + fadeIn(LuminaMotion.contentEnter),
            exit = slideOutVertically(LuminaMotion.chromeOffset) { it / 2 } + fadeOut(LuminaMotion.contentExit),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = transportBottomPadding.coerceAtLeast(0.dp))
        ) {
            if (transportPlayer != null) {
                VideoTransportPill(
                    player = transportPlayer,
                    onScrubbingChange = { isVideoScrubbing = it }
                )
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

private fun computeFittedRect(viewport: IntSize, medium: Medium, bitmap: ImageBitmap): Rect {
    val vw = viewport.width.toFloat()
    val vh = viewport.height.toFloat()
    val isQuarter = ((medium.orientation % 360) + 360) % 360 in setOf(90, 270)
    val naturalW: Float
    val naturalH: Float
    if (medium.width > 0 && medium.height > 0) {
        naturalW = if (isQuarter) medium.height.toFloat() else medium.width.toFloat()
        naturalH = if (isQuarter) medium.width.toFloat() else medium.height.toFloat()
    } else {
        naturalW = bitmap.width.toFloat().coerceAtLeast(1f)
        naturalH = bitmap.height.toFloat().coerceAtLeast(1f)
    }
    val aspect = naturalW / naturalH
    val viewportAspect = vw / vh
    val fitW = if (aspect > viewportAspect) vw else vh * aspect
    val fitH = if (aspect > viewportAspect) vw / aspect else vh
    val left = (vw - fitW) / 2f
    val top = (vh - fitH) / 2f
    return Rect(left, top, left + fitW, top + fitH)
}
