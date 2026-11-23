package org.fossify.gallery.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.home.GalleryTab
import org.fossify.gallery.ui.home.GalleryViewModel
import org.fossify.gallery.ui.home.LuminaHomeScreen
import org.fossify.gallery.ui.navigation.DockMode
import org.fossify.gallery.ui.navigation.LuminaBottomDock
import org.fossify.gallery.ui.navigation.rememberDockScrollState
import org.fossify.gallery.ui.theme.LuminaTheme
import org.fossify.gallery.ui.viewer.AddToAlbumSheet
import org.fossify.gallery.ui.viewer.LuminaViewer
import org.fossify.gallery.ui.viewer.ViewerViewModel

enum class LuminaTab {
    PHOTOS,
    ALBUMS,
    SETTINGS
}

/**
 * Root Lumina application shell.
 * - Keeps LuminaHomeScreen mounted behind LuminaViewer so scroll state is never lost
 *   and predictive-back / drag-to-dismiss reveal the live grid underneath.
 * - Mounts a single persistent morphing LuminaBottomDock across Home, Selection, and Viewer.
 * - Manages modal AddToAlbumSheet.
 */
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
    val scope = rememberCoroutineScope()
    val application = context.applicationContext as Application

    val galleryViewModel: GalleryViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    )
    val viewerViewModel: ViewerViewModel = viewModel()
    val repository = galleryViewModel.repository

    val galleryState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    val viewerState by viewerViewModel.uiState.collectAsStateWithLifecycle()

    val photosGridState = rememberLazyGridState()
    val albumGridState = rememberLazyGridState()
    val dockScrollState = rememberDockScrollState()
    val photosTileBounds = remember { HashMap<Long, Rect>() }
    val albumTileBounds = remember { HashMap<Long, Rect>() }

    var isViewerOpen by remember { mutableStateOf(false) }
    var isFromAlbumViewer by remember { mutableStateOf(false) }
    var viewerInitialIndex by remember { mutableIntStateOf(0) }
    var currentViewerPage by remember { mutableIntStateOf(0) }
    var viewerSourceRect by remember { mutableStateOf<Rect?>(null) }
    var hiddenGridMediaId by remember { mutableStateOf<Long?>(null) }
    var useUnfilteredViewerMedia by remember { mutableStateOf(false) }
    var showAddToAlbumSheet by remember { mutableStateOf(false) }

    LaunchedEffect(pickerMimeType) {
        galleryViewModel.setPickerMimeType(pickerMimeType)
    }

    LaunchedEffect(initialTab, launchRevision) {
        if (initialTab == LuminaTab.ALBUMS.name) {
            galleryViewModel.setTab(GalleryTab.ALBUMS)
        }
    }

    val activeFeed = if (isFromAlbumViewer) galleryState.albumMedia else galleryState.visibleMedia
    val viewerMedia = if (useUnfilteredViewerMedia || activeFeed.isEmpty()) {
        galleryState.allMedia
    } else {
        activeFeed
    }

    LaunchedEffect(initialUri, galleryState.allMedia, launchRevision) {
        if (initialUri != null && galleryState.allMedia.isNotEmpty()) {
            val foundIdx = galleryState.allMedia.indexOfFirst { it.uri == initialUri }
            val resolved = if (foundIdx != -1) foundIdx else 0
            useUnfilteredViewerMedia = true
            isFromAlbumViewer = false
            viewerSourceRect = null
            viewerInitialIndex = resolved
            currentViewerPage = resolved
            isViewerOpen = true
        }
    }

    LaunchedEffect(isViewerOpen, viewerMedia.isEmpty()) {
        if (isViewerOpen && viewerMedia.isEmpty()) {
            isViewerOpen = false
        }
    }

    val safeViewerMedium = viewerMedia.getOrNull(currentViewerPage)
        ?: viewerMedia.getOrNull(viewerInitialIndex)

    val activeGridState = if (galleryState.activeAlbumId != null) albumGridState else photosGridState
    val showScrollToTop by remember(activeGridState) {
        derivedStateOf { activeGridState.firstVisibleItemIndex > 10 }
    }

    LuminaTheme(themeMode = "SYSTEM", dynamicColor = true) {
        Box(modifier = modifier.fillMaxSize()) {
            // 1. Home screen feed
            LuminaHomeScreen(
                viewModel = galleryViewModel,
                photosGridState = photosGridState,
                albumGridState = albumGridState,
                dockScrollState = dockScrollState,
                photosTileBounds = photosTileBounds,
                albumTileBounds = albumTileBounds,
                hiddenMediaId = hiddenGridMediaId,
                isViewerOpen = isViewerOpen,
                onMediaClick = { medium, sourceRect, fromAlbum ->
                    isFromAlbumViewer = fromAlbum
                    if (pickerMode) {
                        if (allowMultiple) {
                            galleryViewModel.toggleSelection(medium.id)
                        } else {
                            onMediaPicked(listOf(medium.uri))
                        }
                    } else {
                        val list = if (fromAlbum) galleryState.albumMedia else galleryState.visibleMedia
                        val idx = list.indexOfFirst { it.id == medium.id }
                        val resolved = if (idx != -1) {
                            useUnfilteredViewerMedia = false
                            idx
                        } else {
                            useUnfilteredViewerMedia = true
                            galleryState.allMedia.indexOfFirst { it.id == medium.id }.coerceAtLeast(0)
                        }
                        viewerSourceRect = sourceRect
                        viewerInitialIndex = resolved
                        currentViewerPage = resolved
                        viewerViewModel.setChromeVisible(true)
                        isViewerOpen = true
                    }
                },
                pickerMode = pickerMode,
                allowMultiple = allowMultiple,
                onConfirmPick = {
                    val picked = galleryState.allMedia
                        .filter { it.id in galleryState.selectedIds }
                        .map { it.uri }
                    onMediaPicked(picked)
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Fullscreen Viewer overlay
            if (isViewerOpen && viewerMedia.isNotEmpty()) {
                LuminaViewer(
                    mediaList = viewerMedia,
                    initialIndex = viewerInitialIndex,
                    initialSourceRect = viewerSourceRect,
                    viewModel = viewerViewModel,
                    repository = repository,
                    onPageChanged = { currentViewerPage = it },
                    onPrepareReturnRect = { targetMediaId ->
                        val activeBounds = if (isFromAlbumViewer) albumTileBounds else photosTileBounds
                        val activeStateForReturn = if (isFromAlbumViewer) albumGridState else photosGridState
                        val existing = activeBounds[targetMediaId]
                        if (existing != null) {
                            existing
                        } else {
                            val gridIdx = if (isFromAlbumViewer) {
                                galleryState.albumMediaIdToGridIndex[targetMediaId]
                            } else {
                                galleryState.mediaIdToGridIndex[targetMediaId]
                            }
                            if (gridIdx != null) {
                                activeStateForReturn.scrollToItem(gridIdx)
                                withFrameNanos { }
                                activeBounds[targetMediaId]
                            } else {
                                null
                            }
                        }
                    },
                    onHiddenGridIdChange = { hiddenGridMediaId = it },
                    onClosingStarted = { isViewerOpen = false },
                    onDismissComplete = {
                        isViewerOpen = false
                        viewerSourceRect = null
                        hiddenGridMediaId = null
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 3. Bottom Dock
            val dockMode: DockMode? = when {
                isViewerOpen && safeViewerMedium != null -> DockMode.Viewer(
                    medium = safeViewerMedium,
                    onAddToAlbumClick = { showAddToAlbumSheet = true },
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
                    onAddToAlbum = { showAddToAlbumSheet = true },
                    onDelete = { galleryViewModel.trashSelected(true) }
                )

                !isViewerOpen -> DockMode.Home(
                    currentTab = galleryState.selectedTab,
                    isCollapsed = dockScrollState.isCollapsed,
                    showScrollToTop = showScrollToTop,
                    isSearchActive = galleryState.isSearchActive,
                    searchQuery = galleryState.searchQuery,
                    activeAlbumName = galleryState.activeAlbumName,
                    onTabSelect = { tab ->
                        dockScrollState.expand()
                        galleryViewModel.setTab(tab)
                    },
                    onSearchOpen = {
                        dockScrollState.expand()
                        galleryViewModel.setSearchActive(true)
                    },
                    onSearchClose = {
                        dockScrollState.expand()
                        galleryViewModel.setSearchActive(false)
                    },
                    onSearchQueryChange = { query ->
                        galleryViewModel.setSearchQuery(query)
                    },
                    onScrollToTop = {
                        dockScrollState.expand()
                        scope.launch {
                            activeGridState.animateScrollToItem(0)
                        }
                    }
                )

                else -> null
            }

            val isDockVisible = when {
                isViewerOpen -> viewerState.isChromeVisible && !viewerState.isDetailsSheetOpen
                galleryState.isMultiSelectMode -> !pickerMode
                else -> true
            }

            if (dockMode != null) {
                LuminaBottomDock(
                    mode = dockMode,
                    visible = isDockVisible,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // 4. Modal Add to Album Bottom Sheet
            if (showAddToAlbumSheet) {
                AddToAlbumSheet(
                    albums = galleryState.albums,
                    itemCount = if (isViewerOpen) 1 else galleryState.selectedIds.size,
                    onSelectAlbum = { albumName, relPath ->
                        val itemsToAdd = if (isViewerOpen) {
                            listOfNotNull(safeViewerMedium)
                        } else {
                            galleryState.allMedia.filter { it.id in galleryState.selectedIds }
                        }
                        galleryViewModel.addMediaToAlbum(itemsToAdd, albumName, relPath)
                        showAddToAlbumSheet = false
                        if (!isViewerOpen) {
                            galleryViewModel.clearSelection()
                        }
                    },
                    onDismiss = { showAddToAlbumSheet = false }
                )
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
