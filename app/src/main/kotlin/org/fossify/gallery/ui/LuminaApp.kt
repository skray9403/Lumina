package org.fossify.gallery.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.data.LuminaMediaRepository
import org.fossify.gallery.ui.home.GalleryViewModel
import org.fossify.gallery.ui.home.LuminaHomeScreen
import org.fossify.gallery.ui.navigation.DockMode
import org.fossify.gallery.ui.navigation.LuminaBottomDock
import org.fossify.gallery.ui.navigation.rememberDockScrollState
import org.fossify.gallery.ui.theme.LuminaMotion
import org.fossify.gallery.ui.theme.LuminaTheme
import org.fossify.gallery.ui.viewer.LuminaViewer
import org.fossify.gallery.ui.viewer.ViewerViewModel

enum class LuminaTab {
    PHOTOS,
    ALBUMS,
    SETTINGS
}

/**
 * Root Lumina application shell.
 *
 * Single persistent morphing bottom dock (Home / Selection / Viewer),
 * real SharedTransitionLayout grid-to-viewer morphing, and filtered viewer scope.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LuminaApp(
    pickerMode: Boolean = false,
    pickerMimeType: String? = null,
    allowMultiple: Boolean = false,
    initialUri: Uri? = null,
    launchRevision: Int = 0,
    initialTab: String = LuminaTab.PHOTOS.name,
    onMediaPicked: (List<Uri>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val galleryViewModel: GalleryViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    )
    val viewerViewModel: ViewerViewModel = viewModel()
    val repository = remember(context) { LuminaMediaRepository(context) }

    val galleryState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    val viewerState by viewerViewModel.uiState.collectAsStateWithLifecycle()
    val dockScrollState = rememberDockScrollState()

    var isViewerOpen by remember { mutableStateOf(false) }
    var viewerInitialIndex by remember { mutableIntStateOf(0) }
    var currentViewerPage by remember { mutableIntStateOf(0) }
    var useUnfilteredViewerMedia by remember { mutableStateOf(false) }

    // Scope viewer to the currently visible timeline (album / search filtered) unless opened via external VIEW intent
    val visibleTimelineMedia = remember(galleryState.timelineSections) {
        galleryState.timelineSections.flatMap { it.items }
    }
    val viewerMedia = if (useUnfilteredViewerMedia || visibleTimelineMedia.isEmpty()) {
        galleryState.allMedia
    } else {
        visibleTimelineMedia
    }

    // Handle incoming VIEW intent with initialUri
    LaunchedEffect(initialUri, galleryState.allMedia, launchRevision) {
        if (initialUri != null && galleryState.allMedia.isNotEmpty()) {
            val foundIdx = galleryState.allMedia.indexOfFirst { it.uri == initialUri }
            val resolved = if (foundIdx != -1) foundIdx else 0
            useUnfilteredViewerMedia = true
            viewerInitialIndex = resolved
            currentViewerPage = resolved
            isViewerOpen = true
        }
    }

    // Close viewer automatically if underlying media list becomes empty (e.g., last item deleted)
    LaunchedEffect(isViewerOpen, viewerMedia.isEmpty()) {
        if (isViewerOpen && viewerMedia.isEmpty()) {
            isViewerOpen = false
        }
    }

    // Keep dock visible when returning from viewer or switching tabs
    LaunchedEffect(isViewerOpen, galleryState.selectedTab, galleryState.isMultiSelectMode) {
        if (!isViewerOpen) {
            dockScrollState.show()
        }
    }

    val safeViewerMedium = viewerMedia.getOrNull(currentViewerPage) ?: viewerMedia.firstOrNull()

    LuminaTheme {
        SharedTransitionLayout {
            Box(modifier = modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = isViewerOpen,
                    transitionSpec = {
                        fadeIn(animationSpec = LuminaMotion.contentEnter) togetherWith
                            fadeOut(animationSpec = LuminaMotion.contentExit)
                    },
                    label = "root_screen_transition"
                ) { viewing ->
                    if (viewing && viewerMedia.isNotEmpty()) {
                        LuminaViewer(
                            mediaList = viewerMedia,
                            initialIndex = viewerInitialIndex,
                            viewModel = viewerViewModel,
                            repository = repository,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedContent,
                            onPageChanged = { currentViewerPage = it },
                            onBack = { isViewerOpen = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LuminaHomeScreen(
                            viewModel = galleryViewModel,
                            dockScrollState = dockScrollState,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedContent,
                            pickerMode = pickerMode,
                            allowMultiple = allowMultiple,
                            onConfirmPick = {
                                val picked = galleryState.allMedia
                                    .filter { it.id in galleryState.selectedIds }
                                    .map { it.uri }
                                onMediaPicked(picked)
                            },
                            onMediaClick = { medium ->
                                if (pickerMode) {
                                    if (allowMultiple) {
                                        galleryViewModel.toggleSelection(medium.id)
                                    } else {
                                        onMediaPicked(listOf(medium.uri))
                                    }
                                } else {
                                    val idx = visibleTimelineMedia.indexOfFirst { it.id == medium.id }
                                    val resolved = if (idx != -1) {
                                        useUnfilteredViewerMedia = false
                                        idx
                                    } else {
                                        useUnfilteredViewerMedia = true
                                        galleryState.allMedia.indexOfFirst { it.id == medium.id }.coerceAtLeast(0)
                                    }
                                    viewerInitialIndex = resolved
                                    currentViewerPage = resolved
                                    isViewerOpen = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Single persistent morphing dock anchored across Home, Multi-Select, and Viewer
                val dockMode: DockMode? = when {
                    isViewerOpen && safeViewerMedium != null -> DockMode.Viewer(
                        medium = safeViewerMedium,
                        isFavorite = safeViewerMedium.isFavorite,
                        onFavoriteToggle = {
                            viewerViewModel.toggleFavorite(repository, safeViewerMedium)
                        },
                        onDetailsClick = {
                            viewerViewModel.loadMetadata(context, safeViewerMedium)
                            viewerViewModel.setDetailsSheetOpen(true)
                        },
                        onDeleteClick = {
                            viewerViewModel.deleteCurrent(repository, safeViewerMedium)
                        }
                    )
                    !isViewerOpen && galleryState.isMultiSelectMode && !pickerMode -> DockMode.Selection(
                        selectedCount = galleryState.selectedIds.size,
                        onShare = {
                            val selectedMedia = galleryState.allMedia.filter { it.id in galleryState.selectedIds }
                            shareMediaItems(context, selectedMedia)
                        },
                        onFavorite = { galleryViewModel.favoriteSelected(true) },
                        onDelete = { galleryViewModel.trashSelected(true) }
                    )
                    !isViewerOpen -> DockMode.Home(
                        currentTab = galleryState.selectedTab,
                        onTabSelect = { galleryViewModel.setTab(it) }
                    )
                    else -> null
                }

                val isDockVisible = when {
                    isViewerOpen -> viewerState.isChromeVisible && !viewerState.isDetailsSheetOpen
                    galleryState.isMultiSelectMode -> !pickerMode
                    else -> dockScrollState.isVisible
                }

                if (dockMode != null) {
                    LuminaBottomDock(
                        mode = dockMode,
                        visible = isDockVisible,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

private fun shareMediaItems(context: Context, items: List<Medium>) {
    if (items.isEmpty()) return
    val uris = ArrayList(items.map { it.uri })
    val shareIntent = Intent().apply {
        if (uris.size == 1) {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uris.first())
            type = items.first().mimeType
        } else {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            type = "*/*"
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share with"))
}
