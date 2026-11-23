package org.fossify.gallery.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.home.GalleryTab
import org.fossify.gallery.ui.theme.LuminaMotion
import org.fossify.gallery.ui.theme.pressScale

@Immutable
sealed interface DockMode {
    data class Home(
        val currentTab: GalleryTab,
        val onTabSelect: (GalleryTab) -> Unit
    ) : DockMode

    data class Selection(
        val selectedCount: Int,
        val onShare: () -> Unit,
        val onFavorite: () -> Unit,
        val onDelete: () -> Unit
    ) : DockMode

    data class Viewer(
        val medium: Medium,
        val isFavorite: Boolean,
        val onFavoriteToggle: () -> Unit,
        val onDetailsClick: () -> Unit,
        val onDeleteClick: () -> Unit
    ) : DockMode
}

/**
 * Single persistent floating dock mounted once in LuminaApp.
 * Morphs in-place across Home, Multi-Select, and Fullscreen Viewer.
 */
@Composable
fun LuminaBottomDock(
    mode: DockMode,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isViewer = mode is DockMode.Viewer

    // Slide and fade strictly in graphicsLayer to avoid layout passes during 120Hz scroll
    val hiddenOffsetPx = with(density) { 120.dp.toPx() }
    val slideProgress by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = LuminaMotion.chromeSlide,
        label = "dock_visibility_slide"
    )

    // Viewer mode isolates dark surface tokens so Light Mode never drops a white pill on black media
    val targetContainerColor = if (isViewer) {
        Color(0xFF1C1F26).copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
    }
    val targetBorderColor = if (isViewer) {
        Color.White.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = LuminaMotion.colorTransition,
        label = "dock_container_color"
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = LuminaMotion.colorTransition,
        label = "dock_border_color"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .graphicsLayer {
                translationY = slideProgress * hiddenOffsetPx
                alpha = (1f - slideProgress * 1.15f).coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = containerColor,
            border = BorderStroke(0.75.dp, borderColor),
            shadowElevation = if (isViewer) 10.dp else 6.dp,
            modifier = Modifier.animateContentSize(animationSpec = LuminaMotion.dockSizeSpec)
        ) {
            AnimatedContent(
                targetState = mode,
                // Key by mode class so photo swipes, favorite toggles, or selection counts
                // update in-place without crossfading the entire dock.
                contentKey = { it::class },
                transitionSpec = {
                    fadeIn(animationSpec = LuminaMotion.contentEnter) togetherWith
                        fadeOut(animationSpec = LuminaMotion.contentExit)
                },
                label = "dock_mode_morph"
            ) { targetMode ->
                when (targetMode) {
                    is DockMode.Home -> HomeDockContent(
                        currentTab = targetMode.currentTab,
                        onTabSelect = targetMode.onTabSelect
                    )
                    is DockMode.Selection -> SelectionDockContent(
                        onShare = targetMode.onShare,
                        onFavorite = targetMode.onFavorite,
                        onDelete = targetMode.onDelete
                    )
                    is DockMode.Viewer -> ViewerDockContent(
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

@Composable
private fun HomeDockContent(
    currentTab: GalleryTab,
    onTabSelect: (GalleryTab) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
    val interactionSource = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = LuminaMotion.colorTransition,
        label = "pill_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = LuminaMotion.colorTransition,
        label = "pill_content"
    )

    Row(
        modifier = Modifier
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(26.dp))
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                role = Role.Tab,
                onClick = onClick
            )
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
private fun SelectionDockContent(
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockActionButton(
            icon = Icons.Default.Share,
            contentDescription = "Share selected",
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onShare
        )
        DockActionButton(
            icon = Icons.Default.FavoriteBorder,
            contentDescription = "Favorite selected",
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onFavorite
        )
        DockActionButton(
            icon = Icons.Default.Delete,
            contentDescription = "Delete selected",
            tint = MaterialTheme.colorScheme.error,
            onClick = onDelete
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
    val iconTint = Color(0xFFF2F4F8)
    val favoriteTint by animateColorAsState(
        targetValue = if (isFavorite) Color(0xFFFF6B81) else iconTint,
        animationSpec = LuminaMotion.colorTransition,
        label = "viewer_favorite_tint"
    )

    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockActionButton(
            icon = Icons.Default.Share,
            contentDescription = "Share",
            tint = iconTint,
            onClick = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, medium.uri)
                    type = medium.mimeType
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share with"))
            }
        )
        DockActionButton(
            icon = Icons.Default.Edit,
            contentDescription = "Edit externally",
            tint = iconTint,
            onClick = {
                val editIntent = Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(medium.uri, medium.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(editIntent, "Edit with"))
            }
        )
        DockActionButton(
            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "Favorite",
            tint = favoriteTint,
            onClick = onFavoriteToggle
        )
        DockActionButton(
            icon = Icons.Default.Info,
            contentDescription = "Details",
            tint = iconTint,
            onClick = onDetailsClick
        )
        DockActionButton(
            icon = Icons.Default.Delete,
            contentDescription = "Delete",
            tint = Color(0xFFFF8A80),
            onClick = onDeleteClick
        )
    }
}

@Composable
private fun DockActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(44.dp)
            .pressScale(interactionSource, pressedScale = 0.88f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
