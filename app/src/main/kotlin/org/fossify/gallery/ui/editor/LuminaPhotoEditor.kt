package org.fossify.gallery.ui.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.engine.image.DecodedImageResult
import org.fossify.gallery.engine.image.NativeImageDecoder
import org.fossify.gallery.models.Medium
import org.fossify.gallery.ui.theme.pressScale
import org.fossify.gallery.ui.theme.rememberLuminaDarkColorScheme
import org.fossify.gallery.ui.theme.rememberLuminaPillTokens
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class EditorTab(val label: String, val icon: ImageVector) {
    CROP("Crop", Icons.Default.Crop),
    ADJUST("Adjust", Icons.Default.Tune),
    FILTERS("Filters", Icons.Default.AutoFixHigh)
}

private enum class AdjustParam(val label: String) {
    BRIGHTNESS("Brightness"),
    CONTRAST("Contrast"),
    SATURATION("Saturation"),
    WARMTH("Warmth")
}

private enum class CropAspect(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    SIXTEEN_NINE("16:9", 16f / 9f),
    THREE_FOUR("3:4", 3f / 4f)
}

private enum class FilterPreset(val label: String) {
    ORIGINAL("Original"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool"),
    MONO("Mono"),
    NOIR("Noir"),
    FADE("Fade")
}

private data class NormCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val isDefault: Boolean get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f
}

private enum class DragHandle { NONE, MOVE, TL, TR, BL, BR }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LuminaPhotoEditor(
    medium: Medium,
    onSaved: (Uri?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkScheme = rememberLuminaDarkColorScheme(dynamicColor = true)
    val pillTokens = rememberLuminaPillTokens(darkOverride = true, colorSchemeOverride = darkScheme)

    var previewBitmap by remember(medium.uri) { mutableStateOf<ImageBitmap?>(null) }
    var activeTab by remember { mutableStateOf(EditorTab.CROP) }
    var activeAdjust by remember { mutableStateOf(AdjustParam.BRIGHTNESS) }
    var activeAspect by remember { mutableStateOf(CropAspect.FREE) }
    var activeFilter by remember { mutableStateOf(FilterPreset.ORIGINAL) }

    var rotationDegrees by remember(medium.uri) { mutableIntStateOf(0) }
    var flipHorizontal by remember(medium.uri) { mutableStateOf(false) }
    var crop by remember(medium.uri) { mutableStateOf(NormCrop()) }

    var brightness by remember(medium.uri) { mutableFloatStateOf(0f) } // -1..1
    var contrast by remember(medium.uri) { mutableFloatStateOf(0f) }   // -1..1
    var saturation by remember(medium.uri) { mutableFloatStateOf(0f) } // -1..1
    var warmth by remember(medium.uri) { mutableFloatStateOf(0f) }     // -1..1

    var isSaving by remember { mutableStateOf(false) }

    val hasEdits = rotationDegrees != 0 || flipHorizontal || !crop.isDefault ||
        brightness != 0f || contrast != 0f || saturation != 0f || warmth != 0f ||
        activeFilter != FilterPreset.ORIGINAL

    LaunchedEffect(medium.uri) {
        val decoded = NativeImageDecoder.decodeImage(context.contentResolver, medium.uri, maxDimensionPx = 2048)
        if (decoded is DecodedImageResult.Static) {
            previewBitmap = decoded.bitmap
        }
    }

    BackHandler(enabled = !isSaving) {
        onClose()
    }

    val colorMatrixValues = remember(brightness, contrast, saturation, warmth, activeFilter) {
        buildCombinedColorMatrixValues(brightness, contrast, saturation, warmth, activeFilter)
    }
    val composeColorFilter = remember(colorMatrixValues) {
        ColorFilter.colorMatrix(ColorMatrix(colorMatrixValues))
    }

    fun resetAll() {
        rotationDegrees = 0
        flipHorizontal = false
        crop = NormCrop()
        activeAspect = CropAspect.FREE
        brightness = 0f
        contrast = 0f
        saturation = 0f
        warmth = 0f
        activeFilter = FilterPreset.ORIGINAL
    }

    fun applyAspectRatio(aspect: CropAspect) {
        activeAspect = aspect
        val targetRatio = aspect.ratio ?: return
        val bmp = previewBitmap ?: return
        val isQuarter = rotationDegrees == 90 || rotationDegrees == 270
        val imgW = (if (isQuarter) bmp.height else bmp.width).toFloat().coerceAtLeast(1f)
        val imgH = (if (isQuarter) bmp.width else bmp.height).toFloat().coerceAtLeast(1f)
        val imgAspect = imgW / imgH

        val normW: Float
        val normH: Float
        if (targetRatio > imgAspect) {
            normW = 1f
            normH = (imgAspect / targetRatio).coerceIn(0.15f, 1f)
        } else {
            normH = 1f
            normW = (targetRatio / imgAspect).coerceIn(0.15f, 1f)
        }
        val l = (1f - normW) / 2f
        val t = (1f - normH) / 2f
        crop = NormCrop(l, t, l + normW, t + normH)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F13))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Floating Top Pill Bar: [ ✕ ]  [ Edit / Reset ]  [ Save ]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(48.dp)
        ) {
            val closeInteraction = remember { MutableInteractionSource() }
            Surface(
                shape = CircleShape,
                color = pillTokens.containerColor,
                border = pillTokens.border,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .pressScale(closeInteraction, pressedScale = 0.90f)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            enabled = !isSaving,
                            interactionSource = closeInteraction,
                            indication = ripple(bounded = true),
                            role = Role.Button,
                            onClick = onClose
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close editor",
                        tint = darkScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (hasEdits) {
                val resetInteraction = remember { MutableInteractionSource() }
                Surface(
                    shape = CircleShape,
                    color = pillTokens.containerColor,
                    border = pillTokens.border,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .height(40.dp)
                        .pressScale(resetInteraction, pressedScale = 0.94f)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                enabled = !isSaving,
                                interactionSource = resetInteraction,
                                indication = ripple(bounded = true),
                                role = Role.Button,
                                onClick = ::resetAll
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset edits",
                            tint = darkScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelLarge,
                            color = darkScheme.onSurface
                        )
                    }
                }
            } else {
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = darkScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            val saveInteraction = remember { MutableInteractionSource() }
            Surface(
                shape = CircleShape,
                color = if (hasEdits && !isSaving) darkScheme.primaryContainer else pillTokens.containerColor,
                border = if (hasEdits) null else pillTokens.border,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(44.dp)
                    .pressScale(saveInteraction, pressedScale = 0.92f)
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            enabled = hasEdits && !isSaving,
                            interactionSource = saveInteraction,
                            indication = ripple(bounded = true),
                            role = Role.Button,
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    val savedUri = exportEditedCopy(
                                        context = context,
                                        medium = medium,
                                        rotationDegrees = rotationDegrees,
                                        flipHorizontal = flipHorizontal,
                                        crop = crop,
                                        colorMatrixValues = colorMatrixValues
                                    )
                                    isSaving = false
                                    onSaved(savedUri)
                                }
                            }
                        )
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isSaving) {
                        LoadingIndicator(
                            color = darkScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save copy",
                            tint = if (hasEdits) darkScheme.onPrimaryContainer else darkScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = if (isSaving) "Saving" else "Save",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasEdits) darkScheme.onPrimaryContainer else darkScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Live Preview + Interactive Crop Box
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        val bmp = previewBitmap
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .onSizeChanged { canvasSize = it },
            contentAlignment = Alignment.Center
        ) {
            if (bmp == null) {
                LoadingIndicator(color = Color.White.copy(alpha = 0.85f))
            } else {
                val isQuarter = rotationDegrees == 90 || rotationDegrees == 270
                val displayW = if (isQuarter) bmp.height.toFloat() else bmp.width.toFloat()
                val displayH = if (isQuarter) bmp.width.toFloat() else bmp.height.toFloat()
                val imageRect = remember(canvasSize, displayW, displayH) {
                    computeFittedImageRect(canvasSize, displayW, displayH)
                }

                // Visual scale correction so rotated bitmap fits cleanly inside imageRect
                val baseFitRect = remember(canvasSize, bmp.width, bmp.height) {
                    computeFittedImageRect(canvasSize, bmp.width.toFloat(), bmp.height.toFloat())
                }
                val rotScale = if (isQuarter && baseFitRect.width > 0f && baseFitRect.height > 0f) {
                    min(imageRect.width / baseFitRect.height, imageRect.height / baseFitRect.width)
                } else {
                    1f
                }

                Image(
                    bitmap = bmp,
                    contentDescription = medium.displayName,
                    contentScale = ContentScale.Fit,
                    colorFilter = composeColorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rotationDegrees.toFloat()
                            scaleX = (if (flipHorizontal) -1f else 1f) * rotScale
                            scaleY = rotScale
                        }
                )

                if (activeTab == EditorTab.CROP && imageRect.width > 1f && imageRect.height > 1f) {
                    CropBoxOverlay(
                        imageRect = imageRect,
                        crop = crop,
                        lockedAspect = activeAspect.ratio,
                        onCropChange = { crop = it }
                    )
                }
            }
        }

        // 3. Tool-specific controls tray
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(106.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (activeTab) {
                EditorTab.CROP -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (aspect in CropAspect.entries) {
                                EditorChip(
                                    label = aspect.label,
                                    selected = activeAspect == aspect,
                                    darkScheme = darkScheme,
                                    onClick = { applyAspectRatio(aspect) }
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EditorIconChip(
                                icon = Icons.Default.RotateRight,
                                label = "Rotate 90°",
                                darkScheme = darkScheme,
                                onClick = {
                                    rotationDegrees = (rotationDegrees + 90) % 360
                                    crop = NormCrop()
                                }
                            )
                            EditorIconChip(
                                icon = Icons.Default.Flip,
                                label = "Flip",
                                darkScheme = darkScheme,
                                onClick = { flipHorizontal = !flipHorizontal }
                            )
                        }
                    }
                }

                EditorTab.ADJUST -> {
                    val currentVal = when (activeAdjust) {
                        AdjustParam.BRIGHTNESS -> brightness
                        AdjustParam.CONTRAST -> contrast
                        AdjustParam.SATURATION -> saturation
                        AdjustParam.WARMTH -> warmth
                    }
                    fun setVal(v: Float) {
                        val clamped = v.coerceIn(-1f, 1f)
                        when (activeAdjust) {
                            AdjustParam.BRIGHTNESS -> brightness = clamped
                            AdjustParam.CONTRAST -> contrast = clamped
                            AdjustParam.SATURATION -> saturation = clamped
                            AdjustParam.WARMTH -> warmth = clamped
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (param in AdjustParam.entries) {
                                val paramVal = when (param) {
                                    AdjustParam.BRIGHTNESS -> brightness
                                    AdjustParam.CONTRAST -> contrast
                                    AdjustParam.SATURATION -> saturation
                                    AdjustParam.WARMTH -> warmth
                                }
                                val badge = if (abs(paramVal) > 0.01f) " ${(paramVal * 100).roundToInt()}" else ""
                                EditorChip(
                                    label = "${param.label}$badge",
                                    selected = activeAdjust == param,
                                    darkScheme = darkScheme,
                                    onClick = { activeAdjust = param }
                                )
                            }
                        }

                        ExpressiveBipolarSlider(
                            value = currentVal,
                            accentColor = darkScheme.primary,
                            onValueChange = ::setVal,
                            onReset = { setVal(0f) }
                        )
                    }
                }

                EditorTab.FILTERS -> {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (preset in FilterPreset.entries) {
                            EditorChip(
                                label = preset.label,
                                selected = activeFilter == preset,
                                darkScheme = darkScheme,
                                onClick = { activeFilter = preset }
                            )
                        }
                    }
                }
            }
        }

        // 4. Bottom Lumina Tool Mode Dock: [ Crop | Adjust | Filters ]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp, top = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = pillTokens.containerColor,
                border = pillTokens.border,
                shadowElevation = 6.dp,
                modifier = Modifier.height(56.dp)
            ) {
                Row(
                    modifier = Modifier.padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (tab in EditorTab.entries) {
                        val selected = activeTab == tab
                        val interaction = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .height(46.dp)
                                .pressScale(interaction, pressedScale = 0.93f)
                                .clip(CircleShape)
                                .background(if (selected) darkScheme.primaryContainer else Color.Transparent)
                                .clickable(
                                    interactionSource = interaction,
                                    indication = ripple(bounded = true),
                                    role = Role.Tab,
                                    onClick = { activeTab = tab }
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) darkScheme.onPrimaryContainer else darkScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) darkScheme.onPrimaryContainer else darkScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropBoxOverlay(
    imageRect: Rect,
    crop: NormCrop,
    lockedAspect: Float?,
    onCropChange: (NormCrop) -> Unit
) {
    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }
    var currentCrop by remember(crop) { mutableStateOf(crop) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageRect, lockedAspect) {
                val hitRadius = 32.dp.toPx()
                val minSpan = 0.14f

                detectDragGestures(
                    onDragStart = { start ->
                        val cl = imageRect.left + currentCrop.left * imageRect.width
                        val ct = imageRect.top + currentCrop.top * imageRect.height
                        val cr = imageRect.left + currentCrop.right * imageRect.width
                        val cb = imageRect.top + currentCrop.bottom * imageRect.height

                        activeHandle = when {
                            (start - Offset(cl, ct)).getDistance() <= hitRadius -> DragHandle.TL
                            (start - Offset(cr, ct)).getDistance() <= hitRadius -> DragHandle.TR
                            (start - Offset(cl, cb)).getDistance() <= hitRadius -> DragHandle.BL
                            (start - Offset(cr, cb)).getDistance() <= hitRadius -> DragHandle.BR
                            start.x in cl..cr && start.y in ct..cb -> DragHandle.MOVE
                            else -> DragHandle.NONE
                        }
                    },
                    onDragEnd = { activeHandle = DragHandle.NONE },
                    onDragCancel = { activeHandle = DragHandle.NONE },
                    onDrag = { change, dragAmount ->
                        if (activeHandle == DragHandle.NONE) return@detectDragGestures
                        change.consume()
                        val dx = dragAmount.x / imageRect.width
                        val dy = dragAmount.y / imageRect.height
                        var l = currentCrop.left
                        var t = currentCrop.top
                        var r = currentCrop.right
                        var b = currentCrop.bottom

                        when (activeHandle) {
                            DragHandle.MOVE -> {
                                val w = r - l
                                val h = b - t
                                l = (l + dx).coerceIn(0f, 1f - w)
                                t = (t + dy).coerceIn(0f, 1f - h)
                                r = l + w
                                b = t + h
                            }
                            DragHandle.TL -> {
                                l = (l + dx).coerceIn(0f, r - minSpan)
                                t = (t + dy).coerceIn(0f, b - minSpan)
                            }
                            DragHandle.TR -> {
                                r = (r + dx).coerceIn(l + minSpan, 1f)
                                t = (t + dy).coerceIn(0f, b - minSpan)
                            }
                            DragHandle.BL -> {
                                l = (l + dx).coerceIn(0f, r - minSpan)
                                b = (b + dy).coerceIn(t + minSpan, 1f)
                            }
                            DragHandle.BR -> {
                                r = (r + dx).coerceIn(l + minSpan, 1f)
                                b = (b + dy).coerceIn(t + minSpan, 1f)
                            }
                            DragHandle.NONE -> Unit
                        }

                        if (lockedAspect != null && activeHandle != DragHandle.MOVE) {
                            val imgAspect = imageRect.width / imageRect.height
                            val normRatio = lockedAspect / imgAspect
                            val curW = (r - l).coerceAtLeast(minSpan)
                            val desiredH = (curW / normRatio).coerceIn(minSpan, 1f)
                            when (activeHandle) {
                                DragHandle.TL, DragHandle.TR -> {
                                    t = (b - desiredH).coerceAtLeast(0f)
                                    val actualW = ((b - t) * normRatio).coerceIn(minSpan, 1f)
                                    if (activeHandle == DragHandle.TL) l = (r - actualW).coerceAtLeast(0f)
                                    else r = (l + actualW).coerceAtMost(1f)
                                }
                                DragHandle.BL, DragHandle.BR -> {
                                    b = (t + desiredH).coerceAtMost(1f)
                                    val actualW = ((b - t) * normRatio).coerceIn(minSpan, 1f)
                                    if (activeHandle == DragHandle.BL) l = (r - actualW).coerceAtLeast(0f)
                                    else r = (l + actualW).coerceAtMost(1f)
                                }
                                else -> Unit
                            }
                        }

                        val next = NormCrop(l, t, r, b)
                        currentCrop = next
                        onCropChange(next)
                    }
                )
            }
    ) {
        val cl = imageRect.left + currentCrop.left * imageRect.width
        val ct = imageRect.top + currentCrop.top * imageRect.height
        val cr = imageRect.left + currentCrop.right * imageRect.width
        val cb = imageRect.top + currentCrop.bottom * imageRect.height
        val cw = (cr - cl).coerceAtLeast(1f)
        val ch = (cb - ct).coerceAtLeast(1f)

        // Dimmed outside veil
        val veil = Color.Black.copy(alpha = 0.56f)
        drawRect(veil, Offset(imageRect.left, imageRect.top), Size(imageRect.width, (ct - imageRect.top).coerceAtLeast(0f)))
        drawRect(veil, Offset(imageRect.left, cb), Size(imageRect.width, (imageRect.bottom - cb).coerceAtLeast(0f)))
        drawRect(veil, Offset(imageRect.left, ct), Size((cl - imageRect.left).coerceAtLeast(0f), ch))
        drawRect(veil, Offset(cr, ct), Size((imageRect.right - cr).coerceAtLeast(0f), ch))

        // Border & rule-of-thirds guides
        drawRect(Color.White.copy(alpha = 0.9f), Offset(cl, ct), Size(cw, ch), style = Stroke(1.5.dp.toPx()))
        val guideColor = Color.White.copy(alpha = 0.32f)
        val guideStroke = 1.dp.toPx()
        for (i in 1..2) {
            val x = cl + cw * i / 3f
            val y = ct + ch * i / 3f
            drawLine(guideColor, Offset(x, ct), Offset(x, cb), guideStroke)
            drawLine(guideColor, Offset(cl, y), Offset(cr, y), guideStroke)
        }

        // Bold corner L-handles
        val arm = 18.dp.toPx()
        val capStroke = 3.5.dp.toPx()
        fun drawCorner(cx: Float, cy: Float, sx: Float, sy: Float) {
            drawLine(Color.White, Offset(cx, cy), Offset(cx + sx * arm, cy), capStroke, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(cx, cy), Offset(cx, cy + sy * arm), capStroke, cap = StrokeCap.Round)
        }
        drawCorner(cl, ct, 1f, 1f)
        drawCorner(cr, ct, -1f, 1f)
        drawCorner(cl, cb, 1f, -1f)
        drawCorner(cr, cb, -1f, -1f)
    }
}

@Composable
private fun ExpressiveBipolarSlider(
    value: Float,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(40.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onReset() },
                    onTap = { pos ->
                        val frac = ((pos.x / size.width.toFloat()).coerceIn(0f, 1f) * 2f) - 1f
                        onValueChange(frac)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val frac = ((change.position.x / size.width.toFloat()).coerceIn(0f, 1f) * 2f) - 1f
                    onValueChange(if (abs(frac) < 0.03f) 0f else frac)
                }
            }
    ) {
        val cy = size.height / 2f
        val trackH = 4.dp.toPx()
        val handleW = 4.dp.toPx()
        val handleH = 24.dp.toPx()
        val midX = size.width / 2f
        val norm = ((value + 1f) / 2f).coerceIn(0f, 1f)
        val handleX = (handleW / 2f + norm * (size.width - handleW))
            .coerceIn(handleW / 2f, size.width - handleW / 2f)

        drawRoundRect(
            color = Color.White.copy(alpha = 0.24f),
            topLeft = Offset(0f, cy - trackH / 2f),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
        )

        val fillLeft = min(midX, handleX)
        val fillW = abs(handleX - midX)
        if (fillW > 1f) {
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(fillLeft, cy - trackH / 2f),
                size = Size(fillW, trackH),
                cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
            )
        }

        // Center zero tick
        drawCircle(Color.White.copy(alpha = 0.65f), radius = 3.dp.toPx(), center = Offset(midX, cy))

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(handleX - handleW / 2f, cy - handleH / 2f),
            size = Size(handleW, handleH),
            cornerRadius = CornerRadius(handleW / 2f, handleW / 2f)
        )
    }
}

@Composable
private fun EditorChip(
    label: String,
    selected: Boolean,
    darkScheme: androidx.compose.material3.ColorScheme,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressScale(interaction, pressedScale = 0.94f)
            .clip(CircleShape)
            .background(if (selected) darkScheme.secondaryContainer else darkScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) darkScheme.onSecondaryContainer else darkScheme.onSurface
        )
    }
}

@Composable
private fun EditorIconChip(
    icon: ImageVector,
    label: String,
    darkScheme: androidx.compose.material3.ColorScheme,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .pressScale(interaction, pressedScale = 0.94f)
            .clip(CircleShape)
            .background(darkScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = darkScheme.onSurface, modifier = Modifier.size(16.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = darkScheme.onSurface)
    }
}

private fun computeFittedImageRect(container: IntSize, imgW: Float, imgH: Float): Rect {
    val cw = container.width.toFloat()
    val ch = container.height.toFloat()
    if (cw <= 0f || ch <= 0f || imgW <= 0f || imgH <= 0f) return Rect.Zero
    val aspect = imgW / imgH
    val containerAspect = cw / ch
    val w = if (aspect > containerAspect) cw else ch * aspect
    val h = if (aspect > containerAspect) cw / aspect else ch
    val l = (cw - w) / 2f
    val t = (ch - h) / 2f
    return Rect(l, t, l + w, t + h)
}

private fun buildCombinedColorMatrixValues(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    warmth: Float,
    preset: FilterPreset
): FloatArray {
    val m = android.graphics.ColorMatrix()

    // 1. Preset base
    when (preset) {
        FilterPreset.ORIGINAL -> Unit
        FilterPreset.VIVID -> {
            m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(1.35f) })
            m.postConcat(contrastMatrix(1.12f))
        }
        FilterPreset.WARM -> m.postConcat(warmthMatrix(0.35f))
        FilterPreset.COOL -> m.postConcat(warmthMatrix(-0.35f))
        FilterPreset.MONO -> m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(0f) })
        FilterPreset.NOIR -> {
            m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(0f) })
            m.postConcat(contrastMatrix(1.38f))
            m.postConcat(brightnessMatrix(-16f))
        }
        FilterPreset.FADE -> {
            m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(0.80f) })
            m.postConcat(contrastMatrix(0.86f))
            m.postConcat(brightnessMatrix(18f))
        }
    }

    // 2. Manual sliders
    if (saturation != 0f) {
        m.postConcat(android.graphics.ColorMatrix().apply { setSaturation((1f + saturation).coerceIn(0f, 2f)) })
    }
    if (contrast != 0f) {
        m.postConcat(contrastMatrix((1f + contrast * 0.7f).coerceIn(0.3f, 1.8f)))
    }
    if (brightness != 0f) {
        m.postConcat(brightnessMatrix(brightness * 90f))
    }
    if (warmth != 0f) {
        m.postConcat(warmthMatrix(warmth * 0.5f))
    }

    return m.array.copyOf()
}

private fun contrastMatrix(scale: Float): android.graphics.ColorMatrix {
    val t = (-0.5f * scale + 0.5f) * 255f
    return android.graphics.ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, t,
            0f, scale, 0f, 0f, t,
            0f, 0f, scale, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

private fun brightnessMatrix(offset: Float): android.graphics.ColorMatrix = android.graphics.ColorMatrix(
    floatArrayOf(
        1f, 0f, 0f, 0f, offset,
        0f, 1f, 0f, 0f, offset,
        0f, 0f, 1f, 0f, offset,
        0f, 0f, 0f, 1f, 0f
    )
)

private fun warmthMatrix(amount: Float): android.graphics.ColorMatrix {
    val r = (1f + amount * 0.35f).coerceIn(0.6f, 1.4f)
    val b = (1f - amount * 0.35f).coerceIn(0.6f, 1.4f)
    return android.graphics.ColorMatrix(
        floatArrayOf(
            r, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

private suspend fun exportEditedCopy(
    context: Context,
    medium: Medium,
    rotationDegrees: Int,
    flipHorizontal: Boolean,
    crop: NormCrop,
    colorMatrixValues: FloatArray
): Uri? = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        val source = ImageDecoder.createSource(resolver, medium.uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val maxSrc = max(info.size.width, info.size.height)
            if (maxSrc > 4096) {
                val scale = 4096f / maxSrc.toFloat()
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1)
                )
            }
        }

        val transformed = if (rotationDegrees != 0 || flipHorizontal) {
            val mat = Matrix().apply {
                if (flipHorizontal) postScale(-1f, 1f, decoded.width / 2f, decoded.height / 2f)
                if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
            }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, mat, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val cx = (crop.left * transformed.width).roundToInt().coerceIn(0, transformed.width - 1)
        val cy = (crop.top * transformed.height).roundToInt().coerceIn(0, transformed.height - 1)
        val cw = ((crop.right - crop.left) * transformed.width).roundToInt().coerceIn(1, transformed.width - cx)
        val ch = ((crop.bottom - crop.top) * transformed.height).roundToInt().coerceIn(1, transformed.height - cy)

        val output = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(colorMatrixValues))
        }
        canvas.drawBitmap(
            transformed,
            android.graphics.Rect(cx, cy, cx + cw, cy + ch),
            android.graphics.Rect(0, 0, cw, ch),
            paint
        )
        transformed.recycle()

        val baseName = medium.displayName.substringBeforeLast('.').ifBlank { "IMG_${System.currentTimeMillis()}" }
        val fileName = "${baseName}_edited_${System.currentTimeMillis() % 10000}.jpg"
        val targetPath = medium.relativePath.takeIf { it.isNotBlank() } ?: "${Environment.DIRECTORY_PICTURES}/Lumina/"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, targetPath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val destUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (destUri == null) {
            output.recycle()
            return@withContext null
        }

        var ok = false
        try {
            resolver.openOutputStream(destUri)?.use { out ->
                ok = output.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        } finally {
            output.recycle()
            if (ok) {
                resolver.update(destUri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            } else {
                resolver.delete(destUri, null, null)
            }
        }

        if (ok) destUri else null
    } catch (_: Exception) {
        null
    }
}
