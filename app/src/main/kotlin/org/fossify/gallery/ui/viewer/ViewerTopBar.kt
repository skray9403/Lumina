package org.fossify.gallery.ui.viewer

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.theme.pressScale
import org.fossify.gallery.ui.theme.rememberLuminaDarkColorScheme
import org.fossify.gallery.ui.theme.rememberLuminaPillTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ViewerTopBar(
    medium: Medium,
    isFavorite: Boolean,
    isVaultMode: Boolean = false,
    onFavoriteToggle: () -> Unit,
    onDetailsClick: () -> Unit,
    onMoveToVaultClick: (() -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val darkScheme = rememberLuminaDarkColorScheme(dynamicColor = true)
    val tokens = rememberLuminaPillTokens(darkOverride = true, colorSchemeOverride = darkScheme)
    var isMenuOpen by remember { mutableStateOf(false) }

    val datePillLabel = remember(medium.dateTakenMs) {
        formatViewerDatePill(medium.dateTakenMs)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                val scrim = Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.60f),
                    0.65f to Color.Black.copy(alpha = 0.22f),
                    1.0f to Color.Transparent
                )
                onDrawBehind { drawRect(brush = scrim) }
            }
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(48.dp)
    ) {
        val backInteraction = remember { MutableInteractionSource() }
        Surface(
            shape = CircleShape,
            color = tokens.containerColor,
            border = tokens.border,
            shadowElevation = tokens.shadowElevation,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .pressScale(backInteraction, pressedScale = 0.90f)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = backInteraction,
                        indication = ripple(bounded = true),
                        role = Role.Button,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = tokens.contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        val dateInteraction = remember { MutableInteractionSource() }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = tokens.containerColor,
            border = tokens.border,
            shadowElevation = tokens.shadowElevation,
            modifier = Modifier
                .align(Alignment.Center)
                .height(42.dp)
                .pressScale(dateInteraction, pressedScale = 0.95f)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = dateInteraction,
                        indication = ripple(bounded = true),
                        role = Role.Button,
                        onClick = onDetailsClick
                    )
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = datePillLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = tokens.contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            val menuInteraction = remember { MutableInteractionSource() }
            Surface(
                shape = CircleShape,
                color = tokens.containerColor,
                border = tokens.border,
                shadowElevation = tokens.shadowElevation,
                modifier = Modifier
                    .size(44.dp)
                    .pressScale(menuInteraction, pressedScale = 0.90f)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = menuInteraction,
                            indication = ripple(bounded = true),
                            role = Role.Button,
                            onClick = { isMenuOpen = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions",
                        tint = tokens.contentColor,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false },
                offset = DpOffset(0.dp, 8.dp),
                shape = RoundedCornerShape(22.dp),
                containerColor = darkScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = tokens.border
            ) {
                val itemPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)

                if (!isVaultMode) {
                    DropdownMenuItem(
                        contentPadding = itemPadding,
                        leadingIcon = {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFavorite) darkScheme.primary else darkScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        text = {
                            Text(
                                text = if (isFavorite) "Remove favorite" else "Favorite",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = darkScheme.onSurface
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            onFavoriteToggle()
                        }
                    )

                    if (onMoveToVaultClick != null) {
                        DropdownMenuItem(
                            contentPadding = itemPadding,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = darkScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = "Move to Locked Folder",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = darkScheme.onSurface
                                )
                            },
                            onClick = {
                                isMenuOpen = false
                                onMoveToVaultClick()
                            }
                        )
                    }
                }

                DropdownMenuItem(
                    contentPadding = itemPadding,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = darkScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = {
                        Text(
                            text = "Details",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = darkScheme.onSurface
                        )
                    },
                    onClick = {
                        isMenuOpen = false
                        onDetailsClick()
                    }
                )

                if (!isVaultMode) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        color = darkScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    if (!medium.isVideo) {
                        DropdownMenuItem(
                            contentPadding = itemPadding,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Wallpaper,
                                    contentDescription = null,
                                    tint = darkScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = "Set as wallpaper",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = darkScheme.onSurface
                                )
                            },
                            onClick = {
                                isMenuOpen = false
                                launchSetWallpaper(context, medium)
                            }
                        )
                    }

                    DropdownMenuItem(
                        contentPadding = itemPadding,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = darkScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        text = {
                            Text(
                                text = "Open with",
                                style = MaterialTheme.typography.bodyMedium,
                                color = darkScheme.onSurface
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(medium.uri, medium.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Open with"))
                        }
                    )

                    DropdownMenuItem(
                        contentPadding = itemPadding,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = darkScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        text = {
                            Text(
                                text = "Use as",
                                style = MaterialTheme.typography.bodyMedium,
                                color = darkScheme.onSurface
                            )
                        },
                        onClick = {
                            isMenuOpen = false
                            val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                                setDataAndType(medium.uri, medium.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Use as"))
                        }
                    )
                }
            }
        }
    }
}

private fun formatViewerDatePill(dateTakenMs: Long): String {
    if (dateTakenMs <= 0L) return "Details"
    val zone = ZoneId.systemDefault()
    val zdt = Instant.ofEpochMilli(dateTakenMs).atZone(zone)
    val sameYear = zdt.year == LocalDate.now(zone).year
    val pattern = if (sameYear) "d MMM · h:mm a" else "d MMM yyyy"
    return zdt.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

private fun launchSetWallpaper(context: Context, medium: Medium) {
    try {
        val intent = WallpaperManager.getInstance(context).getCropAndSetWallpaperIntent(medium.uri)
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
