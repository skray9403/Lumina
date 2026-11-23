package org.fossify.gallery.ui.viewer

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import org.fossify.gallery.models.Medium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerTopBar(
    medium: Medium,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isMenuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                val scrim = Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.72f),
                    0.65f to Color.Black.copy(alpha = 0.34f),
                    1.0f to Color.Transparent
                )
                onDrawBehind {
                    drawRect(brush = scrim)
                }
            }
    ) {
        TopAppBar(
            title = {
                Text(
                    text = medium.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { isMenuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More actions",
                            tint = Color.White
                        )
                    }

                    ViewerOverflowMenu(
                        medium = medium,
                        expanded = isMenuOpen,
                        onDismiss = { isMenuOpen = false },
                        context = context
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
    }
}

@Composable
fun ViewerOverflowMenu(
    medium: Medium,
    expanded: Boolean,
    onDismiss: () -> Unit,
    context: Context
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (!medium.isVideo) {
            DropdownMenuItem(
                text = { Text("Set as wallpaper") },
                onClick = {
                    onDismiss()
                    try {
                        val wallpaperManager = WallpaperManager.getInstance(context)
                        val intent = wallpaperManager.getCropAndSetWallpaperIntent(medium.uri)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val fallback = Intent(Intent.ACTION_ATTACH_DATA).apply {
                            setDataAndType(medium.uri, medium.mimeType)
                            putExtra("mimeType", medium.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(fallback, "Set as"))
                    }
                }
            )
        }

        DropdownMenuItem(
            text = { Text("Open with") },
            onClick = {
                onDismiss()
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(medium.uri, medium.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Open with"))
            }
        )

        DropdownMenuItem(
            text = { Text("Use as") },
            onClick = {
                onDismiss()
                val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                    setDataAndType(medium.uri, medium.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Use as"))
            }
        )
    }
}
