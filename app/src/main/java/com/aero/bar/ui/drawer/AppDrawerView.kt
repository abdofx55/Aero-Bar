package com.aero.bar.ui.drawer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aero.bar.domain.model.AppItem
import com.aero.bar.ui.theme.AeroBarTheme
import com.aero.bar.ui.theme.ElectricCyan

private val PopupSurface = Color(0xFF0C1016)
private val TileSurface = Color(0xFF141A23)
private val CyanSoftBg = Color(0x1F00D2FF)
private val LabelTextColor = Color(0xFFE2E8F0)

/**
 * Floating All Apps Drawer Overlay (860dp Wide, Borderless Very Dark Gray #0C1016, Zero Elevation):
 * - Zero outlines/borders anywhere.
 * - 5-column LazyVerticalGrid displaying the 8 Essential Car Apps + user-installed apps.
 */
@Composable
fun AppDrawerView(
    state: AppDrawerState,
    countdown: Int = 15,
    onResetTimer: () -> Unit = {},
    onIntent: (AppDrawerIntent) -> Unit,
    onDismissOverlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isArabic = remember { java.util.Locale.getDefault().language == "ar" }
    val direction = if (isArabic) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
    var drawerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var selectedItemOffset by remember { mutableStateOf<Offset?>(null) }

    // Clear anchor offset when context menu is dismissed
    LaunchedEffect(state.selectedAppForContext) {
        if (state.selectedAppForContext == null) {
            selectedItemOffset = null
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides direction) {
        Box(
            modifier = modifier
                .width(860.dp)
                .height(480.dp)
                .onGloballyPositioned { drawerCoordinates = it }
                .clip(RoundedCornerShape(30.dp))
                .background(PopupSurface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        onResetTimer()
                        if (state.selectedAppForContext != null) {
                            selectedItemOffset = null
                            onIntent(AppDrawerIntent.OnDismissContextMenu)
                        }
                    }
                )
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. Header Row: Title + Count Pill + Auto-Dismiss Countdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "جميع التطبيقات" else "All Apps",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(CyanSoftBg)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isArabic) "${state.filteredApps.size} تطبيق" else "${state.filteredApps.size} Apps",
                                color = ElectricCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                val gridState = rememberLazyGridState()
                LaunchedEffect(gridState.isScrollInProgress) {
                    if (gridState.isScrollInProgress) {
                        onResetTimer()
                        if (state.selectedAppForContext != null) {
                            selectedItemOffset = null
                            onIntent(AppDrawerIntent.OnDismissContextMenu)
                        }
                    }
                }

                // 2. 5-Column App Grid (Starts from right in Arabic, left in English)
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(
                        items = state.filteredApps,
                        key = { it.packageName }
                    ) { app ->
                        AppDrawerGridItem(
                            app = app,
                            drawerCoordinates = drawerCoordinates,
                            onClick = {
                                onIntent(AppDrawerIntent.OnAppClicked(app))
                                onDismissOverlay()
                            },
                            onLongClick = { offset ->
                                selectedItemOffset = offset
                                onIntent(AppDrawerIntent.OnAppLongClicked(app))
                            }
                        )
                    }
                }
            }

            // 3. Anchored App Long-Press Context Menu Overlay
            if (state.selectedAppForContext != null) {
                val selectedApp = state.selectedAppForContext
                val density = LocalDensity.current
                val itemOffset = selectedItemOffset

                val menuWidth = 280.dp
                val (targetX, targetY) = if (itemOffset != null) {
                    val itemXdp = with(density) { itemOffset.x.toDp() }
                    val itemYdp = with(density) { itemOffset.y.toDp() }
                    // Center context menu horizontally above/below the icon, clamped within drawer padding
                    val x = (itemXdp + 32.dp - (menuWidth / 2)).coerceIn(16.dp, 860.dp - menuWidth - 48.dp)
                    // If icon is in lower half of drawer, pop above; otherwise pop below
                    val y = if (itemYdp > 180.dp) {
                        (itemYdp - 175.dp).coerceAtLeast(16.dp)
                    } else {
                        (itemYdp + 78.dp).coerceAtMost(480.dp - 190.dp)
                    }
                    x to y
                } else {
                    ((860.dp - 48.dp - menuWidth) / 2) to 110.dp
                }

                Box(
                    modifier = Modifier
                        .offset(x = targetX, y = targetY)
                        .animateContentSize()
                ) {
                    AppContextMenu(
                        app = selectedApp,
                        isArabic = isArabic,
                        onDetails = {
                            onIntent(AppDrawerIntent.OnAppDetails(selectedApp))
                            onDismissOverlay()
                        },
                        onUninstall = {
                            onIntent(AppDrawerIntent.OnUninstallApp(selectedApp))
                            onDismissOverlay()
                        },
                        onDismiss = {
                            selectedItemOffset = null
                            onIntent(AppDrawerIntent.OnDismissContextMenu)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawerGridItem(
    app: AppItem,
    drawerCoordinates: LayoutCoordinates?,
    onClick: () -> Unit,
    onLongClick: (Offset?) -> Unit
) {
    val fallbackIcon = getFallbackAppIcon(app.packageName, app.label)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = tween(durationMillis = 90),
        label = "appGridItemScale"
    )
    var itemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { itemCoordinates = it }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    val dCoords = drawerCoordinates
                    val iCoords = itemCoordinates
                    val offset = if (dCoords != null && iCoords != null && dCoords.isAttached && iCoords.isAttached) {
                        dCoords.localPositionOf(iCoords, Offset.Zero)
                    } else null
                    onLongClick(offset)
                }
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 74.dp Borderless Dark Tile (#0C0F14)
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(TileSurface),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = remember(app.icon) { drawableToBitmap(app.icon) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = app.label,
                    tint = ElectricCyan,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Text(
            text = app.label,
            color = LabelTextColor,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private fun getFallbackAppIcon(packageName: String, label: String): ImageVector {
    val lower = (packageName + label).lowercase()
    return when {
        lower.contains("qinggan.app.setting") || lower.contains("car setting") || lower.contains("إعدادات السيارة") || lower.contains("vehicle") -> Icons.Default.DirectionsCar
        lower.contains("android.settings") || lower.contains("إعدادات أندرويد") || lower.contains("setting") -> Icons.Default.Settings
        lower.contains("bluetooth") || lower.contains("phone") || lower.contains("call") -> Icons.Default.Call
        lower.contains("radio") || lower.contains("fm") -> Icons.Default.Radio
        lower.contains("music") || lower.contains("spotify") || lower.contains("audio") -> Icons.Default.MusicNote
        lower.contains("video") || lower.contains("youtube") || lower.contains("vlc") -> Icons.Default.PlayCircle
        lower.contains("carplay") || lower.contains("carlife") || lower.contains("plink") -> Icons.Default.DirectionsCar
        lower.contains("dvr") || lower.contains("camera") || lower.contains("record") -> Icons.Default.Videocam
        lower.contains("map") || lower.contains("nav") -> Icons.Default.Navigation
        else -> Icons.Default.Apps
    }
}

private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
    if (drawable == null) return null
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@Preview(
    name = "App Drawer Overlay - 5 Columns Borderless Pure Black",
    widthDp = 900,
    heightDp = 420,
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
fun AppDrawerViewPreview() {
    AeroBarTheme {
        AppDrawerView(
            state = AppDrawerState(
                filteredApps = listOf(
                    AppItem("com.qinggan.bluetoothphone", "", "البلوتوث"),
                    AppItem("com.qinggan.app.radio", "", "الراديو"),
                    AppItem("com.qinggan.app.music", "", "الموسيقى"),
                    AppItem("com.qinggan.app.video", "", "الفيديو"),
                    AppItem("com.qinggan.app.vehicle", "", "التكييف والمقاعد"),
                    AppItem("com.qinggan.app.setting", "", "الإعدادات"),
                    AppItem("com.pateo.CarPlay.App", "", "CarPlay"),
                    AppItem("com.miren.dvr", "", "كاميرا التسجيل"),
                    AppItem("com.google.android.apps.maps", "", "خرائط جوجل"),
                    AppItem("com.spotify.music", "", "سبوتيفاي")
                ),
                isLoading = false
            ),
            onIntent = {}
        )
    }
}
