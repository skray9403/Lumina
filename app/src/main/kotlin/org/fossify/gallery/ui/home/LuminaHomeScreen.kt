package org.fossify.gallery.ui.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.fossify.gallery.models.Medium

@Composable
fun LuminaHomeScreen(
    viewModel: GalleryViewModel,
    onMediaClick: (Medium) -> Unit,
    modifier: Modifier = Modifier,
    bottomDockPadding: PaddingValues = PaddingValues(bottom = 96.dp)
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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

    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                AnimatedVisibility(
                    visible = state.isMultiSelectMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SelectionTopBar(
                        selectedCount = state.selectedIds.size,
                        onClearSelection = { viewModel.clearSelection() },
                        onSelectAll = { viewModel.selectAll() },
                        onShare = {
                            val selectedMedia = state.allMedia.filter { it.id in state.selectedIds }
                            if (selectedMedia.isNotEmpty()) {
                                val uris = ArrayList(selectedMedia.map { it.uri })
                                val shareIntent = Intent().apply {
                                    if (uris.size == 1) {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_STREAM, uris.first())
                                        type = selectedMedia.first().mimeType
                                    } else {
                                        action = Intent.ACTION_SEND_MULTIPLE
                                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                        type = "*/*"
                                    }
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share with"))
                            }
                        },
                        onFavorite = { viewModel.favoriteSelected(true) },
                        onDelete = { viewModel.trashSelected(true) }
                    )
                }

                if (!state.isMultiSelectMode) {
                    SearchBarOverlay(
                        searchQuery = state.searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) }
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            when (state.selectedTab) {
                GalleryTab.PHOTOS -> {
                    MediaTimelineGrid(
                        sections = state.timelineSections,
                        columnCount = state.columnCount,
                        selectedIds = state.selectedIds,
                        isMultiSelectMode = state.isMultiSelectMode,
                        onMediaClick = onMediaClick,
                        onMediaLongClick = { medium ->
                            viewModel.toggleSelection(medium.id)
                        },
                        contentPadding = bottomDockPadding
                    )
                }
                GalleryTab.ALBUMS -> {
                    AlbumsGrid(
                        albums = state.albums,
                        onAlbumClick = { album ->
                            viewModel.filterByAlbum(album.bucketId)
                        },
                        contentPadding = bottomDockPadding
                    )
                }
            }
        }
    }
}
