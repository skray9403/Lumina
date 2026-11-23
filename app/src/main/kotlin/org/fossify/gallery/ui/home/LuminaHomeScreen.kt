package org.fossify.gallery.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.navigation.DockScrollState
import org.fossify.gallery.ui.theme.LuminaMotion
import org.fossify.gallery.ui.theme.TopEdgeFeatherFade

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LuminaHomeScreen(
    viewModel: GalleryViewModel,
    dockScrollState: DockScrollState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onMediaClick: (Medium) -> Unit,
    modifier: Modifier = Modifier,
    pickerMode: Boolean = false,
    allowMultiple: Boolean = false,
    onConfirmPick: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var activeAlbumName by remember { mutableStateOf<String?>(null) }
    var hasMediaPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // API 33+ granular runtime permission request
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.READ_MEDIA_IMAGES] == true ||
            perms[Manifest.permission.READ_MEDIA_VIDEO] == true ||
            (Build.VERSION.SDK_INT >= 34 && perms[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true)
        hasMediaPermission = granted
        if (granted) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        if (Build.VERSION.SDK_INT >= 34) {
            permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
        val needsRequest = permissions.any { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
        if (needsRequest) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            hasMediaPermission = true
        }
    }

    // Clear active album badge if user switches back to Albums tab
    LaunchedEffect(state.selectedTab) {
        if (state.selectedTab == GalleryTab.ALBUMS) {
            activeAlbumName = null
        }
    }

    // Exit multi-select on system back gesture
    BackHandler(enabled = state.isMultiSelectMode) {
        viewModel.clearSelection()
    }

    // MediaStore scoped action launcher (Delete, Trash, Favorite)
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
        top = statusBarTop + 72.dp,
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
                fadeIn(animationSpec = LuminaMotion.contentEnter) togetherWith
                    fadeOut(animationSpec = LuminaMotion.contentExit)
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
                            onRequestPermission = {
                                val permissions = mutableListOf(
                                    Manifest.permission.READ_MEDIA_IMAGES,
                                    Manifest.permission.READ_MEDIA_VIDEO
                                )
                                if (Build.VERSION.SDK_INT >= 34) {
                                    permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                                }
                                permissionLauncher.launch(permissions.toTypedArray())
                            },
                            contentPadding = gridContentPadding
                        )
                    } else {
                        MediaTimelineGrid(
                            sections = state.timelineSections,
                            columnCount = state.columnCount,
                            selectedIds = state.selectedIds,
                            isMultiSelectMode = state.isMultiSelectMode,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onMediaClick = onMediaClick,
                            onMediaLongClick = { medium ->
                                viewModel.toggleSelection(medium.id)
                            },
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

        // 2. Top-edge feather fade over scrolling media
        TopEdgeFeatherFade(
            height = statusBarTop + 88.dp,
            surfaceColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 3. Fixed-height floating top chrome (zero vertical jump between Search and Selection)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            AnimatedContent(
                targetState = state.isMultiSelectMode,
                transitionSpec = {
                    fadeIn(animationSpec = LuminaMotion.contentEnter) togetherWith
                        fadeOut(animationSpec = LuminaMotion.contentExit)
                },
                label = "top_chrome_swap"
            ) { selecting ->
                if (selecting) {
                    SelectionTopBar(
                        selectedCount = state.selectedIds.size,
                        showConfirmPick = pickerMode && allowMultiple,
                        onClearSelection = { viewModel.clearSelection() },
                        onSelectAll = { viewModel.selectAll() },
                        onConfirmPick = onConfirmPick
                    )
                } else {
                    SearchBarOverlay(
                        searchQuery = state.searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        activeAlbumName = activeAlbumName,
                        onAlbumBackClick = if (activeAlbumName != null) {
                            {
                                activeAlbumName = null
                                viewModel.setTab(GalleryTab.ALBUMS)
                            }
                        } else {
                            null
                        }
                    )
                }
            }
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
