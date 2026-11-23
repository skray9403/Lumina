package org.fossify.gallery.ui.viewer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fossify.gallery.engine.video.PlayerPool
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.data.LuminaMediaRepository
import org.fossify.gallery.ui.details.LuminaDetailsBottomSheet
import org.fossify.gallery.ui.theme.LuminaMotion

/**
 * Full-screen immersive media viewer.
 *
 * Shares the root persistent LuminaBottomDock via LuminaApp, owns a single PlayerPool,
 * and enforces light-on-dark system bar icons independent of app theme.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LuminaViewer(
    mediaList: List<Medium>,
    initialIndex: Int,
    viewModel: ViewerViewModel,
    repository: LuminaMediaRepository,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onPageChanged: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(mediaList.isEmpty()) {
        if (mediaList.isEmpty()) {
            onBack()
        }
    }
    if (mediaList.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black))
        return
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val safeInitial = initialIndex.coerceIn(0, mediaList.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeInitial,
        pageCount = { mediaList.size }
    )

    var canPagerSwipe by remember { mutableStateOf(true) }

    // Single PlayerPool hoisted for the entire viewer lifecycle
    val playerPool = remember(context) { PlayerPool(context) }
    DisposableEffect(playerPool) {
        onDispose {
            playerPool.release()
        }
    }

    // MediaStore scoped action launcher (Delete, Trash, Favorite)
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

    // Enforce dark-canvas system bar icons for the viewer lifetime and restore on exit
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

    // Toggle system bar visibility without re-running onDispose on every tap
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

    // Pause video cleanly when swiping onto a static photo without racing sibling video pages
    LaunchedEffect(currentMedium.id) {
        if (!currentMedium.isVideo) {
            playerPool.pause()
        }
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Horizontal Pager
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = canPagerSwipe,
            modifier = Modifier.fillMaxSize(),
            key = { index -> mediaList.getOrNull(index)?.id ?: index.toLong() }
        ) { page ->
            val medium = mediaList.getOrNull(page)
            if (medium != null) {
                val isCurrentPage = page == pagerState.currentPage
                val pageModifier = if (isCurrentPage) {
                    with(sharedTransitionScope) {
                        Modifier
                            .fillMaxSize()
                            .sharedBounds(
                                rememberSharedContentState(key = "media_${medium.id}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> LuminaMotion.spatialStandard() }
                            )
                    }
                } else {
                    Modifier.fillMaxSize()
                }

                if (medium.isVideo) {
                    VideoPage(
                        medium = medium,
                        isCurrentPage = isCurrentPage,
                        playerPool = playerPool,
                        onSingleTap = { viewModel.toggleChrome() },
                        modifier = pageModifier
                    )
                } else {
                    PhotoPage(
                        medium = medium,
                        onSingleTap = { viewModel.toggleChrome() },
                        onEdgeSwipeHandoff = { canPagerSwipe = it },
                        modifier = pageModifier
                    )
                }
            }
        }

        // 2. Gradient-backed top bar
        AnimatedVisibility(
            visible = state.isChromeVisible,
            enter = slideInVertically(
                animationSpec = LuminaMotion.chromeOffset,
                initialOffsetY = { -it }
            ) + fadeIn(animationSpec = LuminaMotion.contentEnter),
            exit = slideOutVertically(
                animationSpec = LuminaMotion.chromeOffset,
                targetOffsetY = { -it }
            ) + fadeOut(animationSpec = LuminaMotion.contentExit),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ViewerTopBar(
                medium = currentMedium,
                onBack = onBack,
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
