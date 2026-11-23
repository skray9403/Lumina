package org.fossify.gallery.ui.viewer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.collectAsState
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
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.data.LuminaMediaRepository
import org.fossify.gallery.ui.details.LuminaDetailsBottomSheet
import org.fossify.gallery.ui.navigation.DockMode
import org.fossify.gallery.ui.navigation.LuminaBottomDock

/**
 * Native, immersive full-screen photo & video viewer for Lumina.
 *
 * Employs HorizontalPager for smooth photo swiping, single-tap chrome toggling with
 * WindowInsetsControllerCompat status/nav bar synchronization, and zero legacy wrappers.
 */
@Composable
fun LuminaViewer(
    mediaList: List<Medium>,
    initialIndex: Int,
    viewModel: ViewerViewModel,
    repository: LuminaMediaRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (mediaList.isEmpty()) {
        onBack()
        return
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.uiState.collectAsState()

    val safeInitial = initialIndex.coerceIn(0, mediaList.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeInitial,
        pageCount = { mediaList.size }
    )

    var canPagerSwipe by remember { mutableStateOf(true) }

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

    // Sync WindowInsetsControllerCompat with chrome visibility
    DisposableEffect(state.isChromeVisible) {
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (state.isChromeVisible) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val currentMedium = mediaList.getOrNull(pagerState.currentPage) ?: mediaList.first()

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setCurrentIndex(pagerState.currentPage)
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Horizontal Pager media content
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = canPagerSwipe,
            modifier = Modifier.fillMaxSize(),
            key = { index -> mediaList.getOrNull(index)?.id ?: index.toLong() }
        ) { page ->
            val medium = mediaList.getOrNull(page)
            if (medium != null) {
                if (medium.isVideo) {
                    VideoPage(
                        medium = medium,
                        isCurrentPage = page == pagerState.currentPage,
                        onSingleTap = { viewModel.toggleChrome() }
                    )
                } else {
                    PhotoPage(
                        medium = medium,
                        onSingleTap = { viewModel.toggleChrome() },
                        onEdgeSwipeHandoff = { canPagerSwipe = it }
                    )
                }
            }
        }

        // 2. Animated top bar
        AnimatedVisibility(
            visible = state.isChromeVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ViewerTopBar(
                medium = currentMedium,
                onBack = onBack,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 3. Animated bottom action dock
        AnimatedVisibility(
            visible = state.isChromeVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            LuminaBottomDock(
                mode = DockMode.Viewer(
                    medium = currentMedium,
                    isFavorite = currentMedium.isFavorite,
                    onFavoriteToggle = { viewModel.toggleFavorite(repository, currentMedium) },
                    onDetailsClick = {
                        viewModel.loadMetadata(context, currentMedium)
                        viewModel.setDetailsSheetOpen(true)
                    },
                    onDeleteClick = { viewModel.deleteCurrent(repository, currentMedium) }
                )
            )
        }

        // 4. Details Modal Bottom Sheet
        if (state.isDetailsSheetOpen) {
            LuminaDetailsBottomSheet(
                metadata = state.currentMetadata,
                isLoading = state.isLoadingMetadata,
                onDismiss = { viewModel.setDetailsSheetOpen(false) }
            )
        }
    }
}
