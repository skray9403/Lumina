package org.fossify.gallery.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.data.LuminaMediaRepository
import org.fossify.gallery.ui.home.GalleryTab
import org.fossify.gallery.ui.home.GalleryViewModel
import org.fossify.gallery.ui.home.LuminaHomeScreen
import org.fossify.gallery.ui.navigation.DockMode
import org.fossify.gallery.ui.navigation.LuminaBottomDock
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
 * Implements single-activity architecture, SharedTransitionLayout morphing between
 * timeline grid and fullscreen viewer, and non-bouncy spring motion.
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
    val galleryViewModel: GalleryViewModel = viewModel()
    val viewerViewModel: ViewerViewModel = viewModel()
    val repository = remember { LuminaMediaRepository(context) }

    val galleryState by galleryViewModel.uiState.collectAsStateWithLifecycle()

    var activeViewerIndex by remember { mutableStateOf<Int?>(null) }

    // Handle incoming VIEW intent with initialUri
    LaunchedEffect(initialUri, galleryState.allMedia, launchRevision) {
        if (initialUri != null && galleryState.allMedia.isNotEmpty()) {
            val foundIdx = galleryState.allMedia.indexOfFirst { it.uri == initialUri }
            if (foundIdx != -1) {
                activeViewerIndex = foundIdx
            } else {
                activeViewerIndex = 0
            }
        }
    }

    LuminaTheme {
        SharedTransitionLayout {
            Box(modifier = modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = activeViewerIndex,
                    transitionSpec = {
                        fadeIn(animationSpec = LuminaMotion.contentEnter) togetherWith
                                fadeOut(animationSpec = LuminaMotion.contentExit)
                    },
                    label = "root_screen_transition"
                ) { viewerIndex ->
                    if (viewerIndex != null && galleryState.allMedia.isNotEmpty()) {
                        LuminaViewer(
                            mediaList = galleryState.allMedia,
                            initialIndex = viewerIndex,
                            viewModel = viewerViewModel,
                            repository = repository,
                            onBack = { activeViewerIndex = null },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LuminaHomeScreen(
                                viewModel = galleryViewModel,
                                onMediaClick = { medium ->
                                    if (pickerMode) {
                                        onMediaPicked(listOf(medium.uri))
                                    } else {
                                        val idx = galleryState.allMedia.indexOfFirst { it.id == medium.id }
                                        activeViewerIndex = if (idx != -1) idx else 0
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Floating bottom dock (Home mode)
                            if (!galleryState.isMultiSelectMode) {
                                LuminaBottomDock(
                                    mode = DockMode.Home(
                                        currentTab = galleryState.selectedTab,
                                        onTabSelect = { galleryViewModel.setTab(it) }
                                    ),
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
