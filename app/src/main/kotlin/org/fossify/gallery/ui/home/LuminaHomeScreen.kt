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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.navigation.DockScrollState
import org.fossify.gallery.ui.theme.LuminaMotion
import org.fossify.gallery.ui.theme.TopEdgeFeatherFade

@Composable
fun LuminaHomeScreen(
    viewModel: GalleryViewModel,
    gridState: LazyGridState,
    dockScrollState: DockScrollState,
    tileBounds: MutableMap<Long, Rect>,
    hiddenMediaId: Long?,
    onMediaClick: (Medium, Rect?) -> Unit,
    modifier: Modifier = Modifier,
    pickerMode: Boolean = false,
    allowMultiple: Boolean = false,
    onConfirmPick: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var activeAlbumName by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(state.selectedTab) {
        if (state.selectedTab == GalleryTab.ALBUMS) {
            activeAlbumName = null
        }
    }

    BackHandler(enabled = state.isMultiSelectMode) {
        viewModel.clearSelection()
    }

    BackHandler(enabled = !state.isMultiSelectMode && state.isSearchActive) {
        viewModel.setSearchActive(false)
    }

    BackHandler(enabled = !state.isMultiSelectMode && !state.isSearchActive && activeAlbumName != null) {
        activeAlbumName = null
        viewModel.setTab(GalleryTab.ALBUMS)
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
        top = statusBarTop + 60.dp,
        bottom = navBarBottom + 96.dp
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Full-bleed scrolling feed
        AnimatedContent(
            targetState = state.selectedTab,
            transitionSpec = {
                fadeIn(LuminaMotion.throughZeroEnter) togetherWith
                    fadeOut(LuminaMotion.throughZeroExit)
            },
            label = "home_tab_content",
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(dockScrollState.nestedScrollConnection)
        ) { tab ->
            when (tab) {
                GalleryTab.PHOTOS -> {
                    if (state.timelineSections.isEmpty()) {
                        EmptyOrPermissionState(
                            hasPermission = hasMediaPermission,
                            onRequestPermission = ::requestPermissions,
                            contentPadding = gridContentPadding
                        )
                    } else {
                        MediaTimelineGrid(
                            sections = state.timelineSections,
                            columnCount = state.columnCount,
                            gridState = gridState,
                            selectedIds = state.selectedIds,
                            isMultiSelectMode = state.isMultiSelectMode,
                            hiddenMediaId = hiddenMediaId,
                            tileBounds = tileBounds,
                            onMediaClick = onMediaClick,
                            onMediaLongClick = { medium -> viewModel.toggleSelection(medium.id) },
                            contentPadding = gridContentPadding
                        )
                    }
                }

                GalleryTab.ALBUMS -> {
                    AlbumsGrid(
                        albums = state.albums,
                        onAlbumClick = { album ->
                            activeAlbumName = album.name
                            viewModel.filterByAlbum(album.bucketId)
                        },
                        contentPadding = gridContentPadding
                    )
                }
            }
        }

        // 2. Subtle dark top scrim so status bar icons remain legible over bright photos
        TopEdgeFeatherFade(
            height = statusBarTop + 64.dp,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 3. Reference floating top chrome (centered date pill + trailing ⋮, or Search / Selection)
        FloatingTopChrome(
            gridState = gridState,
            indexToDatePill = state.indexToDatePill,
            selectedTab = state.selectedTab,
            isMultiSelectMode = state.isMultiSelectMode,
            selectedCount = state.selectedIds.size,
            isSearchActive = state.isSearchActive,
            searchQuery = state.searchQuery,
            activeAlbumName = activeAlbumName,
            columnCount = state.columnCount,
            showConfirmPick = pickerMode && allowMultiple,
            onQueryChange = { viewModel.setSearchQuery(it) },
            onCloseSearch = { viewModel.setSearchActive(false) },
            onAlbumBackClick = {
                activeAlbumName = null
                viewModel.setTab(GalleryTab.ALBUMS)
            },
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
