package org.fossify.gallery.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.fossify.gallery.engine.vault.VaultManager
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.home.GalleryTab
import org.fossify.gallery.ui.home.GalleryViewModel
import org.fossify.gallery.ui.home.LuminaHomeScreen
import org.fossify.gallery.ui.home.findItemBoundsInRoot
import org.fossify.gallery.ui.navigation.DockMode
import org.fossify.gallery.ui.navigation.LuminaBottomDock
import org.fossify.gallery.ui.navigation.rememberDockScrollState
import org.fossify.gallery.ui.theme.LuminaTheme
import org.fossify.gallery.ui.viewer.AddToAlbumSheet
import org.fossify.gallery.ui.viewer.LuminaViewer
import org.fossify.gallery.ui.viewer.UpnpCastSheet
import org.fossify.gallery.ui.viewer.ViewerViewModel

enum class LuminaTab { PHOTOS, ALBUMS, SETTINGS }

@Composable
fun LuminaApp(
    pickerMode: Boolean = false,
    pickerMimeType: String? = null,
    allowMultiple: Boolean = false,
    initialUri: Uri? = null,
    launchRevision: Int = 0,
    initialTab: String = LuminaTab.PHOTOS.name,
    upnpCastMediaId: Long? = null,
    upnpCastRevision: Int = 0,
    onMediaPicked: (List<Uri>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val application = context.applicationContext as Application

    val galleryViewModel: GalleryViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    )
    val viewerViewModel: ViewerViewModel = viewModel()
    val repository = galleryViewModel.repository

    val galleryState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    val viewerState by viewerViewModel.uiState.collectAsStateWithLifecycle()
    val isVaultUnlocked by VaultManager.isUnlocked.collectAsStateWithLifecycle()

    val photosGridState = rememberLazyGridState()
    val albumsGridState = rememberLazyGridState()
    val albumGridState = rememberLazyGridState()
    val dockScrollState = rememberDockScrollState()

    var photosGridRootOffset by remember { mutableStateOf(Offset.Zero) }
    var albumGridRootOffset by remember { mutableStateOf(Offset.Zero) }

    var isViewerOpen by remember { mutableStateOf(false) }
    var isViewerClosing by remember { mutableStateOf(false) }
    var isVideoActionDockVisible by remember { mutableStateOf(true) }
    var isShareMenuOpen by remember { mutableStateOf(false) }
    var viewerOpenedFromAlbum by remember { mutableStateOf(false) }
    var viewerOpenedFromTrash by remember { mutableStateOf(false) }
    var viewerOpenedFromVault by remember { mutableStateOf(false) }
    var viewerInitialIndex by remember { mutableIntStateOf(0) }
    var currentViewerPage by remember { mutableIntStateOf(0) }
    var viewerSourceRect by remember { mutableStateOf<Rect?>(null) }
    var hiddenGridMediaId by remember { mutableStateOf<Long?>(null) }
    var useUnfilteredViewerMedia by remember { mutableStateOf(false) }

    var pendingAddToAlbumItems by remember { mutableStateOf<List<Medium>?>(null) }
    var upnpCastTarget by remember { mutableStateOf<Medium?>(null) }

    // Auto-close Locked Folder / Vault Viewer whenever VaultManager locks on backgrounding
    LaunchedEffect(isVaultUnlocked, galleryState.isVaultOverlay, viewerOpenedFromVault) {
        if (!isVaultUnlocked) {
            if (viewerOpenedFromVault) {
                isViewerOpen = false
                isViewerClosing = false
                viewerOpenedFromVault = false
            }
            if (galleryState.isVaultOverlay) {
                galleryViewModel.closeAlbum()
            }
        }
    }

    // Enforce FLAG_SECURE while viewing Locked Folder media fullscreen
    DisposableEffect(isViewerOpen, viewerOpenedFromVault, activity) {
        if (isViewerOpen && viewerOpenedFromVault) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (viewerOpenedFromVault) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    LaunchedEffect(pickerMimeType) { galleryViewModel.setPickerMimeType(pickerMimeType) }

    LaunchedEffect(initialTab, launchRevision) {
        if (initialTab == LuminaTab.ALBUMS.name) galleryViewModel.setTab(GalleryTab.ALBUMS)
    }

    // Handle native Android Sharesheet -> "UPnP Cast" action callback
    LaunchedEffect(upnpCastMediaId, upnpCastRevision) {
        if (upnpCastMediaId != null && upnpCastRevision > 0) {
            val matched = galleryState.allMedia.firstOrNull { it.id == upnpCastMediaId }
            if (matched != null) {
                upnpCastTarget = matched
            }
        }
    }

    val viewerMedia = when {
        useUnfilteredViewerMedia -> galleryState.allMedia
        viewerOpenedFromAlbum -> galleryState.albumMedia
        galleryState.visibleMedia.isNotEmpty() -> galleryState.visibleMedia
        else -> galleryState.allMedia
    }

    LaunchedEffect(initialUri, galleryState.allMedia, launchRevision) {
        if (initialUri != null && galleryState.allMedia.isNotEmpty()) {
            val foundIdx = galleryState.allMedia.indexOfFirst { it.uri == initialUri }
            val resolved = if (foundIdx != -1) foundIdx else 0
            useUnfilteredViewerMedia = true
            viewerOpenedFromAlbum = false
            viewerOpenedFromTrash = false
            viewerOpenedFromVault = false
            viewerSourceRect = null
            viewerInitialIndex = resolved
            currentViewerPage = resolved
            isVideoActionDockVisible = true
            isViewerClosing = false
            isViewerOpen = true
        }
    }

    LaunchedEffect(isViewerOpen, viewerMedia.isEmpty()) {
        if (isViewerOpen && viewerMedia.isEmpty()) {
            isViewerOpen = false
            isViewerClosing = false
            isVideoActionDockVisible = true
        }
    }

    LaunchedEffect(isViewerOpen, galleryState.selectedTab, galleryState.activeAlbumId, galleryState.isMultiSelectMode) {
        if (!isViewerOpen) dockScrollState.expand()
    }

    fun moveItemsToLockedFolder(items: List<Medium>) {
        if (items.isEmpty()) return
        VaultManager.unlock(
            context = context,
            title = "Move to Locked Folder",
            subtitle = "Verify identity to encrypt ${if (items.size == 1) "item" else "${items.size} items"}"
        ) { ok, err ->
            if (!ok) {
                if (!err.isNullOrBlank()) Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                return@unlock
            }
            scope.launch {
                val encryptedOriginals = VaultManager.importFromMediaStore(context, items)
                if (encryptedOriginals.isNotEmpty()) {
                    Toast.makeText(
                        context,
                        "Encrypted ${encryptedOriginals.size} item(s). Confirm to remove original.",
                        Toast.LENGTH_SHORT
                    ).show()
                    galleryViewModel.requestPermanentDeleteForUris(encryptedOriginals)
                    if (galleryState.isMultiSelectMode) galleryViewModel.clearSelection()
                } else {
                    Toast.makeText(context, "Failed to encrypt items.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val activeScrollGridState = when {
        galleryState.activeAlbumId != null -> albumGridState
        galleryState.selectedTab == GalleryTab.ALBUMS -> albumsGridState
        else -> photosGridState
    }
    val showScrollToTop by remember(activeScrollGridState, galleryState.selectedTab, galleryState.activeAlbumId) {
        derivedStateOf {
            val isGridVisible = galleryState.activeAlbumId != null || galleryState.selectedTab == GalleryTab.PHOTOS
            isGridVisible && activeScrollGridState.firstVisibleItemIndex >= 15
        }
    }

    val safeViewerMedium = viewerMedia.getOrNull(currentViewerPage) ?: viewerMedia.firstOrNull()

    LuminaTheme {
        Box(modifier = modifier.fillMaxSize()) {
            LuminaHomeScreen(
                viewModel = galleryViewModel,
                photosGridState = photosGridState,
                albumsGridState = albumsGridState,
                albumGridState = albumGridState,
                dockScrollState = dockScrollState,
                onPhotosGridRootOffsetChange = { photosGridRootOffset = it },
                onAlbumGridRootOffsetChange = { albumGridRootOffset = it },
                hiddenMediaId = hiddenGridMediaId,
                isViewerOpen = isViewerOpen,
                pickerMode = pickerMode,
                allowMultiple = allowMultiple,
                onConfirmPick = {
                    val picked = galleryState.allMedia
                        .filter { it.id in galleryState.selectedIds }
                        .map { it.uri }
                    onMediaPicked(picked)
                },
                onMediaClick = { medium, fromAlbum, fromTrash, fromVault ->
                    if (pickerMode) {
                        if (allowMultiple) galleryViewModel.toggleSelection(medium.id)
                        else onMediaPicked(listOf(medium.uri))
                    } else {
                        val feed = if (fromAlbum) galleryState.albumMedia else galleryState.visibleMedia
                        val targetGrid = if (fromAlbum) albumGridState else photosGridState
                        val rootOffset = if (fromAlbum) albumGridRootOffset else photosGridRootOffset
                        val sourceRect = targetGrid.findItemBoundsInRoot(rootOffset, medium.id)

                        val idx = feed.indexOfFirst { it.id == medium.id }
                        val resolved = if (idx != -1) {
                            useUnfilteredViewerMedia = false
                            viewerOpenedFromAlbum = fromAlbum
                            viewerOpenedFromTrash = fromTrash
                            viewerOpenedFromVault = fromVault
                            idx
                        } else {
                            useUnfilteredViewerMedia = true
                            viewerOpenedFromAlbum = false
                            viewerOpenedFromTrash = false
                            viewerOpenedFromVault = false
                            galleryState.allMedia.indexOfFirst { it.id == medium.id }.coerceAtLeast(0)
                        }
                        viewerSourceRect = sourceRect
                        viewerInitialIndex = resolved
                        currentViewerPage = resolved
                        isVideoActionDockVisible = true
                        isShareMenuOpen = false
                        isViewerClosing = false
                        viewerViewModel.setChromeVisible(true)
                        isViewerOpen = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isViewerOpen && viewerMedia.isNotEmpty()) {
                LuminaViewer(
                    mediaList = viewerMedia,
                    initialIndex = viewerInitialIndex,
                    initialSourceRect = viewerSourceRect,
                    viewModel = viewerViewModel,
                    repository = repository,
                    isShareMenuOpen = isShareMenuOpen,
                    isVaultMode = viewerOpenedFromVault,
                    onMoveToVault = if (!viewerOpenedFromVault && !viewerOpenedFromTrash) {
                        { medium -> moveItemsToLockedFolder(listOf(medium)) }
                    } else {
                        null
                    },
                    onPageChanged = { currentViewerPage = it },
                    onPrepareReturnRect = { targetMediaId ->
                        val targetGrid = if (viewerOpenedFromAlbum) albumGridState else photosGridState
                        val rootOffset = if (viewerOpenedFromAlbum) albumGridRootOffset else photosGridRootOffset
                        val indexMap = if (viewerOpenedFromAlbum) {
                            galleryState.albumMediaIdToGridIndex
                        } else {
                            galleryState.mediaIdToGridIndex
                        }
                        val existing = targetGrid.findItemBoundsInRoot(rootOffset, targetMediaId)
                        if (existing != null) {
                            existing
                        } else {
                            val gridIdx = indexMap[targetMediaId]
                            if (gridIdx != null) {
                                targetGrid.scrollToItem(gridIdx)
                                withFrameNanos { }
                                targetGrid.findItemBoundsInRoot(rootOffset, targetMediaId)
                            } else {
                                null
                            }
                        }
                    },
                    onHiddenGridIdChange = { hiddenGridMediaId = it },
                    onVideoActionDockVisibleChange = { isVideoActionDockVisible = it },
                    onClosingStarted = {
                        isViewerClosing = true
                        isVideoActionDockVisible = true
                        isShareMenuOpen = false
                    },
                    onDismissComplete = {
                        isViewerOpen = false
                        isViewerClosing = false
                        isVideoActionDockVisible = true
                        isShareMenuOpen = false
                        viewerOpenedFromTrash = false
                        viewerOpenedFromVault = false
                        viewerSourceRect = null
                        hiddenGridMediaId = null
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            val dockInViewerMode = isViewerOpen && !isViewerClosing && safeViewerMedium != null
            val dockMode: DockMode? = when {
                dockInViewerMode && safeViewerMedium != null -> DockMode.Viewer(
                    medium = safeViewerMedium,
                    isTrashMode = viewerOpenedFromTrash,
                    isVaultMode = viewerOpenedFromVault,
                    onAddToAlbumClick = { pendingAddToAlbumItems = listOf(safeViewerMedium) },
                    onRestoreClick = {
                        if (viewerOpenedFromVault) {
                            scope.launch {
                                val count = VaultManager.unlockItemsToGallery(context, listOf(safeViewerMedium))
                                if (count > 0) {
                                    galleryViewModel.refresh()
                                    Toast.makeText(context, "Moved out of Locked Folder", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            viewerViewModel.restoreCurrent(repository, safeViewerMedium)
                        }
                    },
                    onUpnpCastClick = { upnpCastTarget = safeViewerMedium },
                    onDeleteClick = {
                        when {
                            viewerOpenedFromVault -> {
                                scope.launch {
                                    VaultManager.deletePermanently(context, listOf(safeViewerMedium))
                                    Toast.makeText(context, "Deleted permanently", Toast.LENGTH_SHORT).show()
                                }
                            }
                            viewerOpenedFromTrash -> viewerViewModel.deletePermanently(repository, safeViewerMedium)
                            else -> viewerViewModel.deleteCurrent(repository, safeViewerMedium)
                        }
                    }
                )

                !dockInViewerMode && galleryState.isMultiSelectMode && !pickerMode -> DockMode.Selection(
                    selectedCount = galleryState.selectedIds.size,
                    isTrashMode = galleryState.isTrashOverlay,
                    isVaultMode = galleryState.isVaultOverlay,
                    onShare = {
                        val selectedMedia = galleryState.allMedia.filter { it.id in galleryState.selectedIds }
                        shareMediaItems(context, selectedMedia)
                    },
                    onAddToAlbum = {
                        val selectedMedia = galleryState.allMedia.filter { it.id in galleryState.selectedIds }
                        if (selectedMedia.isNotEmpty()) pendingAddToAlbumItems = selectedMedia
                    },
                    onMoveToVault = {
                        val selectedMedia = galleryState.allMedia.filter { it.id in galleryState.selectedIds }
                        moveItemsToLockedFolder(selectedMedia)
                    },
                    onRestore = {
                        if (galleryState.isVaultOverlay) {
                            val selectedVault = galleryState.vaultMedia.filter { it.id in galleryState.selectedIds }
                            scope.launch {
                                val count = VaultManager.unlockItemsToGallery(context, selectedVault)
                                if (count > 0) {
                                    galleryViewModel.refresh()
                                    galleryViewModel.clearSelection()
                                    Toast.makeText(context, "Unlocked $count item(s)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            galleryViewModel.restoreSelected()
                        }
                    },
                    onDelete = {
                        when {
                            galleryState.isVaultOverlay -> {
                                val selectedVault = galleryState.vaultMedia.filter { it.id in galleryState.selectedIds }
                                scope.launch {
                                    VaultManager.deletePermanently(context, selectedVault)
                                    galleryViewModel.clearSelection()
                                    Toast.makeText(context, "Deleted permanently", Toast.LENGTH_SHORT).show()
                                }
                            }
                            galleryState.isTrashOverlay -> galleryViewModel.deleteSelectedPermanently()
                            else -> galleryViewModel.trashSelected(true)
                        }
                    }
                )

                !dockInViewerMode -> DockMode.Home(
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
                    onSearchQueryChange = { galleryViewModel.setSearchQuery(it) },
                    onScrollToTop = {
                        dockScrollState.expand()
                        scope.launch { activeScrollGridState.animateScrollToItem(0) }
                    }
                )

                else -> null
            }

            val isDockVisible = when {
                dockInViewerMode -> viewerState.isChromeVisible &&
                    isVideoActionDockVisible &&
                    !viewerState.isDetailsSheetOpen &&
                    pendingAddToAlbumItems == null &&
                    upnpCastTarget == null
                galleryState.isMultiSelectMode -> !pickerMode
                else -> true
            }

            if (dockMode != null) {
                LuminaBottomDock(
                    mode = dockMode,
                    visible = isDockVisible,
                    onShareMenuOpenChange = { isShareMenuOpen = it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            val itemsToAdd = pendingAddToAlbumItems
            if (itemsToAdd != null) {
                AddToAlbumSheet(
                    albums = galleryState.albums,
                    itemCount = itemsToAdd.size,
                    onSelectAlbum = { albumName, relativePath ->
                        pendingAddToAlbumItems = null
                        galleryViewModel.addMediaToAlbum(
                            items = itemsToAdd,
                            albumName = albumName,
                            targetRelativePath = relativePath
                        ) { copied ->
                            val msg = if (copied > 0) "Added to $albumName" else "Couldn't add to $albumName"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (galleryState.isMultiSelectMode) galleryViewModel.clearSelection()
                        }
                    },
                    onDismiss = { pendingAddToAlbumItems = null }
                )
            }

            val castMedium = upnpCastTarget
            if (castMedium != null) {
                UpnpCastSheet(
                    medium = castMedium,
                    onDismiss = { upnpCastTarget = null }
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
