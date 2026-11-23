package org.fossify.gallery.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.navigation.DockScrollState
import org.fossify.gallery.ui.theme.BottomEdgeFeatherFade
import org.fossify.gallery.ui.theme.LuminaMotion
import org.fossify.gallery.ui.theme.TopEdgeFeatherFade

@Composable
fun LuminaHomeScreen(
    viewModel: GalleryViewModel,
    photosGridState: LazyGridState,
    albumGridState: LazyGridState,
    dockScrollState: DockScrollState,
    photosTileBounds: MutableMap<Long, Rect>,
    albumTileBounds: MutableMap<Long, Rect>,
    hiddenMediaId: Long?,
    isViewerOpen: Boolean,
    onMediaClick: (Medium, Rect?, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pickerMode: Boolean = false,
    allowMultiple: Boolean = false,
    onConfirmPick: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    fun hasAnyMediaAccess(): Boolean {
        val fullImages = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        val fullVideo = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED
        val partial = Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
        return fullImages || fullVideo || partial
    }

    var hasMediaPermission by remember { mutableStateOf(hasAnyMediaAccess()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.READ_MEDIA_IMAGES] == true ||
            perms[Manifest.permission.READ_MEDIA_VIDEO] == true ||
            (Build.VERSION.SDK_INT >= 34 && perms[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true)
        hasMediaPermission = granted || hasAnyMediaAccess()
        if (hasMediaPermission) {
            viewModel.refresh()
        }
    }

    fun requestPermissions() {
        val perms = buildList {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            if (Build.VERSION.SDK_INT >= 34) {
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(Unit) {
        if (!hasAnyMediaAccess()) {
            requestPermissions()
        } else {
            hasMediaPermission = true
        }
    }

    // Back priority: Viewer -> Multi-select -> Search -> Album Overlay (Predictive) -> Albums tab -> Photos tab
    BackHandler(enabled = !isViewerOpen && state.isMultiSelectMode) {
        viewModel.clearSelection()
    }

    BackHandler(enabled = !isViewerOpen && !state.isMultiSelectMode && state.isSearchActive) {
        viewModel.setSearchActive(false)
    }

    BackHandler(
        enabled = !isViewerOpen && !state.isMultiSelectMode && !state.isSearchActive &&
            state.activeAlbumId == null && state.selectedTab == GalleryTab.ALBUMS
    ) {
        viewModel.setTab(GalleryTab.PHOTOS)
    }

    val actionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) {
        viewModel.clearPendingIntentRequest()
        viewModel.clearSelection()
    }

    LaunchedEffect(state.pendingIntentRequest) {
        state.pendingIntentRequest?.let { req ->
            actionLauncher.launch(req)
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gridContentPadding = PaddingValues(
        top = statusBarTop + 62.dp,
        bottom = navBarBottom + 96.dp
    )

    val slideShiftPx = with(density) { 24.dp.roundToPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Directional shared-axis Photos <-> Albums transition
        AnimatedContent(
            targetState = state.selectedTab,
            transitionSpec = {
                val toAlbums = targetState == GalleryTab.ALBUMS
                val enterDir = if (toAlbums) slideShiftPx else -slideShiftPx
                val exitDir = if (toAlbums) -slideShiftPx else slideShiftPx
                (slideInHorizontally(LuminaMotion.tabSlideSpec) { enterDir } +
                    fadeIn(LuminaMotion.tabFadeIn)) togetherWith
                    (slideOutHorizontally(LuminaMotion.tabSlideSpec) { exitDir } +
                        fadeOut(LuminaMotion.tabFadeOut))
            },
            label = "home_tab_shared_axis",
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(dockScrollState.nestedScrollConnection)
        ) { tab ->
            when (tab) {
                GalleryTab.PHOTOS -> {
                    if (state.visibleMedia.isEmpty()) {
                        EmptyOrPermissionState(
                            hasPermission = hasMediaPermission,
                            onRequestPermission = ::requestPermissions,
                            contentPadding = gridContentPadding
                        )
                    } else {
                        MediaTimelineGrid(
                            media = state.visibleMedia,
                            columnCount = state.columnCount,
                            gridState = photosGridState,
                            selectedIds = state.selectedIds,
                            isMultiSelectMode = state.isMultiSelectMode,
                            hiddenMediaId = if (state.activeAlbumId == null) hiddenMediaId else null,
                            tileBounds = photosTileBounds,
                            onMediaClick = { medium, rect -> onMediaClick(medium, rect, false) },
                            onMediaLongClick = { medium -> viewModel.toggleSelection(medium.id) },
                            contentPadding = gridContentPadding
                        )
                    }
                }

                GalleryTab.ALBUMS -> {
                    AlbumsGrid(
                        albums = state.albums,
                        onAlbumClick = { album -> viewModel.openAlbum(album) },
                        contentPadding = gridContentPadding
                    )
                }
            }
        }

        // 2. Theme-matched top & bottom feather fades (ChatGPT-style non-glass integration)
        TopEdgeFeatherFade(
            height = statusBarTop + 68.dp,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        BottomEdgeFeatherFade(
            height = navBarBottom + 98.dp,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 3. Top floating pills (when not inside Album overlay)
        if (state.activeAlbumId == null) {
            FloatingTopChrome(
                gridState = if (state.selectedTab == GalleryTab.PHOTOS) photosGridState else null,
                indexToDatePill = state.indexToDatePill,
                staticTitle = if (state.selectedTab == GalleryTab.ALBUMS) "Albums" else null,
                isMultiSelectMode = state.isMultiSelectMode,
                selectedCount = state.selectedIds.size,
                columnCount = state.columnCount,
                showConfirmPick = pickerMode && allowMultiple,
                onColumnCountChange = { viewModel.setColumnCount(it) },
                onRefresh = { viewModel.refresh() },
                onClearSelection = { viewModel.clearSelection() },
                onSelectAll = { viewModel.selectAll() },
                onConfirmPick = onConfirmPick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // 4. Stacked Album Overlay with native Predictive Back revealing live AlbumsGrid underneath
        if (state.activeAlbumId != null) {
            AlbumDetailOverlay(
                albumName = state.activeAlbumName ?: "Album",
                media = state.albumMedia,
                indexToDatePill = state.albumIndexToDatePill,
                gridState = albumGridState,
                dockScrollState = dockScrollState,
                columnCount = state.columnCount,
                selectedIds = state.selectedIds,
                isMultiSelectMode = state.isMultiSelectMode,
                hiddenMediaId = hiddenMediaId,
                tileBounds = albumTileBounds,
                showConfirmPick = pickerMode && allowMultiple,
                enabledBack = !isViewerOpen && !state.isMultiSelectMode && !state.isSearchActive,
                onMediaClick = { medium, rect -> onMediaClick(medium, rect, true) },
                onMediaLongClick = { medium -> viewModel.toggleSelection(medium.id) },
                onColumnCountChange = { viewModel.setColumnCount(it) },
                onRefresh = { viewModel.refresh() },
                onClearSelection = { viewModel.clearSelection() },
                onSelectAll = { viewModel.selectAll() },
                onConfirmPick = onConfirmPick,
                onCloseComplete = { viewModel.closeAlbum() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun EmptyOrPermissionState(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            if (!hasPermission) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Permission needed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Lumina needs permission to access photos and videos on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onRequestPermission,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Grant Permission")
                }
            } else {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "No photos",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Photos or Videos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Media on your device will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
