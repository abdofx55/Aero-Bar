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
import com.aero.bar.ui.icons.AeroIcons
import com.aero.bar.ui.theme.AeroBarTheme
import com.aero.bar.ui.theme.ElectricCyan

private val PopupSurface = Color(0xFF0C1016)
private val TileSurface = Color(0xFF141A23)
private val CyanSoftBg = Color(0x1F00D2FF)
private val LabelTextColor = Color(0xFFE2E8F0)

private val OEM_GARBAGE_PACKAGES = setOf(
    "com.qualcomm.qti.logkit",
    "com.touch.opentouch",
    "com.touch.improvetouch",
    "com.dongfeng.dab",
    "com.dongfeng.radio",
    "com.android.camera2"
)

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
                // 1. Header Row: Title + Tabs (All vs Hidden) + Auto-Filter OEM button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "قائمة التطبيقات" else "App Drawer",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        // Segmented Switch: All vs Hidden
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF141A23))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tab 1: All Apps
                            val isAllSelected = state.currentTab == AppDrawerTab.ALL
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isAllSelected) ElectricCyan else Color.Transparent)
                                    .clickable {
                                        onResetTimer()
                                        onIntent(AppDrawerIntent.OnTabChanged(AppDrawerTab.ALL))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isArabic) "الكل (${state.apps.size})" else "All (${state.apps.size})",
                                    color = if (isAllSelected) Color(0xFF0C1016) else Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Tab 2: Hidden Apps
                            val isHiddenSelected = state.currentTab == AppDrawerTab.HIDDEN
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isHiddenSelected) Color(0xFFF59E0B) else Color.Transparent)
                                    .clickable {
                                        onResetTimer()
                                        onIntent(AppDrawerIntent.OnTabChanged(AppDrawerTab.HIDDEN))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AeroIcons.IcEyeOff,
                                    contentDescription = null,
                                    tint = if (isHiddenSelected) Color(0xFF0C1016) else Color(0xFFF59E0B),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isArabic) "المخفية (${state.hiddenApps.size})" else "Hidden (${state.hiddenApps.size})",
                                    color = if (isHiddenSelected) Color(0xFF0C1016) else Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Right Side: Auto-Filter OEM button if garbage apps exist in visible list
                    val hasOemGarbageVisible = state.apps.any { it.packageName in OEM_GARBAGE_PACKAGES }
                    if (state.currentTab == AppDrawerTab.ALL && hasOemGarbageVisible) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x22F59E0B))
                                .clickable {
                                    onResetTimer()
                                    onIntent(AppDrawerIntent.OnAutoFilterOemApps)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isArabic) "⚡ إخفاء أدوات المصنع الزائدة" else "⚡ Auto-Hide OEM Tools",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
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

                // 2. Body: Either Empty State or 5-Column App Grid
                if (state.currentTab == AppDrawerTab.HIDDEN && state.hiddenApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF141A23)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AeroIcons.IcEye,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Text(
                                text = if (isArabic) "لا توجد تطبيقات مخفية حتى الآن" else "No Hidden Apps",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isArabic) "اضغط مطولاً على أي تطبيق في القائمة الرئيسية لإخفائه هنا" else "Long-press any app in the main list to hide it here",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(
                            items = state.filteredApps,
                            key = { it.packageName }
                        ) { app ->
                            AppDrawerGridItem(
                                app = app,
                                drawerCoordinates = drawerCoordinates,
                                isArabic = isArabic,
                                onClick = {
                                    if (state.currentTab == AppDrawerTab.HIDDEN) {
                                        onIntent(AppDrawerIntent.OnUnhideApp(app))
                                    } else {
                                        onIntent(AppDrawerIntent.OnAppClicked(app))
                                        onDismissOverlay()
                                    }
                                },
                                onLongClick = { offset ->
                                    selectedItemOffset = offset
                                    onIntent(AppDrawerIntent.OnAppLongClicked(app))
                                },
                                onUnhide = {
                                    onIntent(AppDrawerIntent.OnUnhideApp(app))
                                }
                            )
                        }
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
                        onHide = {
                            if (selectedApp.isHidden) {
                                onIntent(AppDrawerIntent.OnUnhideApp(selectedApp))
                            } else {
                                onIntent(AppDrawerIntent.OnHideApp(selectedApp))
                            }
                            selectedItemOffset = null
                            onIntent(AppDrawerIntent.OnDismissContextMenu)
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
    isArabic: Boolean,
    onClick: () -> Unit,
    onLongClick: (Offset?) -> Unit,
    onUnhide: () -> Unit = {}
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
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 74.dp Borderless Dark Tile (#141A23)
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
                        .graphicsLayer {
                            if (app.isHidden) alpha = 0.65f
                        }
                )
            } else {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = app.label,
                    tint = if (app.isHidden) Color(0xFFF59E0B) else ElectricCyan,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Text(
            text = app.label,
            color = LabelTextColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (app.isHidden) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF10B981))
                    .clickable { onUnhide() }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isArabic) "استعادة" else "Unhide",
                    color = Color(0xFF0F172A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
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
