package org.fossify.gallery.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoSizeSelectActual
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.home.GalleryTab
import org.fossify.gallery.ui.theme.LuminaMotion

sealed interface DockMode {
    data class Home(val currentTab: GalleryTab, val onTabSelect: (GalleryTab) -> Unit) : DockMode
    data class Viewer(
        val medium: Medium,
        val isFavorite: Boolean,
        val onFavoriteToggle: () -> Unit,
        val onDetailsClick: () -> Unit,
        val onDeleteClick: () -> Unit
    ) : DockMode
}

/**
 * MonoMail-inspired floating morphing dock for Lumina.
 *
 * Morphs smoothly in-place between Home mode (Photos / Albums) and Viewer mode
 * (5-action dock: Share, Edit, Favorite, Details, Delete) using critically damped spring motion.
 */
@Composable
fun LuminaBottomDock(
    mode: DockMode,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            shadowElevation = 8.dp,
            tonalElevation = 6.dp,
            modifier = Modifier.animateContentSize(animationSpec = LuminaMotion.standardSpring())
        ) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    fadeIn(animationSpec = LuminaMotion.contentEnter) togetherWith
                            fadeOut(animationSpec = LuminaMotion.contentExit)
                },
                label = "dock_morph_transition"
            ) { targetMode ->
                when (targetMode) {
                    is DockMode.Home -> {
                        HomeDockContent(
                            currentTab = targetMode.currentTab,
                            onTabSelect = targetMode.onTabSelect
                        )
                    }
                    is DockMode.Viewer -> {
                        ViewerDockContent(
                            medium = targetMode.medium,
                            isFavorite = targetMode.isFavorite,
                            onFavoriteToggle = targetMode.onFavoriteToggle,
                            onDetailsClick = targetMode.onDetailsClick,
                            onDeleteClick = targetMode.onDeleteClick,
                            context = context
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeDockContent(
    currentTab: GalleryTab,
    onTabSelect: (GalleryTab) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockTabPill(
            title = "Photos",
            icon = Icons.Default.PhotoSizeSelectActual,
            isSelected = currentTab == GalleryTab.PHOTOS,
            onClick = { onTabSelect(GalleryTab.PHOTOS) }
        )
        DockTabPill(
            title = "Albums",
            icon = Icons.Default.PhotoLibrary,
            isSelected = currentTab == GalleryTab.ALBUMS,
            onClick = { onTabSelect(GalleryTab.ALBUMS) }
        )
    }
}

@Composable
private fun DockTabPill(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ViewerDockContent(
    medium: Medium,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onDetailsClick: () -> Unit,
    onDeleteClick: () -> Unit,
    context: Context
) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Share
        IconButton(
            onClick = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, medium.uri)
                    type = medium.mimeType
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share with"))
            }
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // 2. Edit (fires external ACTION_EDIT chooser)
        IconButton(
            onClick = {
                val editIntent = Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(medium.uri, medium.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(editIntent, "Edit with"))
            }
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit externally",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // 3. Favorite
        IconButton(onClick = onFavoriteToggle) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

        // 4. Details
        IconButton(onClick = onDetailsClick) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // 5. Delete
        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
