package com.aero.bar.ui.recents

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aero.bar.data.repository.RecentAppsRepositoryImpl
import com.aero.bar.domain.model.RecentTaskItem
import com.aero.bar.ui.icons.AeroIcons
import com.aero.bar.ui.theme.*
import java.util.Locale

private val PopupSurface = Color(0xFF0C1016)
private val CardSurface = Color(0xFF141A23)
private val PillSurface = Color(0xFF141A23)
private val CyanSoftBg = Color(0x1F00D2FF)
private val RoseSoftBg = Color(0x24F43F5E)
private val RoseText = Color(0xFFFB7185)
private val MutedText = Color(0xFF94A3B8)
private val CloseButtonBg = Color(0xFF222A38)

/**
 * Floating Recent Apps Overlay (860dp Wide, Borderless Very Dark Gray #0C1016 + 10.dp subtle elevation):
 * - Clean cards with top-right [X] dismiss button, large app icon, app label, and RAM in Electric Cyan.
 * - Minimal, clutter-free header with RAM telemetry and Clear All.
 * - Bilingual: Defaults to English, naturally switches to Arabic when system language is Arabic.
 */
@Composable
fun RecentAppsView(
    state: RecentAppsState,
    countdown: Int = 15,
    onResetTimer: () -> Unit = {},
    onIntent: (RecentAppsIntent) -> Unit,
    onDismissOverlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isArabic = remember { Locale.getDefault().language == "ar" }
    val direction = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Reset countdown when scrolling tasks list
    androidx.compose.runtime.LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            onResetTimer()
        }
    }

    // Reset scroll to the first app (index 0) whenever the overlay opens or top task changes
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (state.recentTasks.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }
    androidx.compose.runtime.LaunchedEffect(state.recentTasks.firstOrNull()?.persistentId) {
        if (state.recentTasks.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Box(
            modifier = modifier
                .width(860.dp)
                .wrapContentHeight()
                .clip(RoundedCornerShape(30.dp))
                .background(PopupSurface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onResetTimer() } // Consume clicks and reset countdown
                )
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Header: Title + Count + Auto-Dismiss Countdown | System RAM Telemetry Pill | Clear All Button
                RecentAppsTopHeader(
                    isArabic = isArabic,
                    activeCount = state.recentTasks.size,
                    countdown = countdown,
                    usedRamMb = state.totalRamUsedMb,
                    totalRamMb = state.totalRamDeviceMb,
                    onClearAll = {
                        onResetTimer()
                        onIntent(RecentAppsIntent.OnClearAllTasks)
                    }
                )

                // Cards Carousel or Empty State
                if (state.recentTasks.isEmpty()) {
                    RecentAppsEmptyState(isArabic = isArabic)
                } else {
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(
                            items = state.recentTasks,
                            key = { it.packageName }
                        ) { task ->
                            RecentAppCardExact(
                                task = task,
                                onOpen = {
                                    onIntent(RecentAppsIntent.OnTaskClicked(task))
                                    onDismissOverlay()
                                },
                                onClose = {
                                    onIntent(RecentAppsIntent.OnDismissTask(task))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentAppsTopHeader(
    isArabic: Boolean,
    activeCount: Int,
    countdown: Int,
    usedRamMb: Int,
    totalRamMb: Int,
    onClearAll: () -> Unit
) {
    val safeTotal = totalRamMb.coerceAtLeast(1024)
    val safeUsed = usedRamMb.coerceIn(128, safeTotal)
    val usedGb = safeUsed / 1024f
    val totalGb = safeTotal / 1024f
    val ratio = (safeUsed.toFloat() / safeTotal.toFloat()).coerceIn(0.08f, 1f)
    val percent = (ratio * 100).toInt()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title + Count Pill + Countdown Badge
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isArabic) "التطبيقات المفتوحة" else "Recent Apps",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(CyanSoftBg)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isArabic) "$activeCount نشط" else "$activeCount Active",
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // System RAM Telemetry Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(PillSurface)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RAM",
                color = MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF1A2230))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(ratio)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(KineticMint, ElectricCyan)
                            )
                        )
                )
            }

            Text(
                text = String.format(Locale.US, "%d / %d MB (%d%%)", safeUsed, safeTotal, percent),
                color = ElectricCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Clear All Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(RoseSoftBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClearAll
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isArabic) "مسح الكل" else "Clear All",
                color = RoseText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * Exact Card Design matching media_1793942363786.png:
 * - Rounded dark card (140dp x 165dp).
 * - Top-right subtle circular [X] close button.
 * - Center large crisp app icon.
 * - App title in bold white.
 * - RAM usage in Electric Cyan (#00D2FF).
 * - Entire card is clickable to open/switch to the app.
 */
@Composable
private fun RecentAppCardExact(
    task: RecentTaskItem,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    val isBluetoothProtected = task.packageName in RecentAppsRepositoryImpl.BLUETOOTH_PROTECTED_PACKAGES
    val fallbackIcon = getFallbackIcon(task.packageName, task.label)

    Box(
        modifier = Modifier
            .width(142.dp)
            .height(168.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen
            )
            .padding(10.dp)
    ) {
        // 1. Top-Right Dismiss [X] Button (Circular with 38dp touch target)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(38.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(CloseButtonBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AeroIcons.IcCloseX,
                    contentDescription = "Close Task",
                    tint = MutedText,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        // Top-Left Smart Bluetooth Indicator (if applicable)
        if (isBluetoothProtected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(6.dp))
                    .background(KineticMint.copy(alpha = 0.16f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "BT",
                    color = KineticMint,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. Center Content: Icon + App Title + Cyan RAM Usage
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFF141923)),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = remember(task.icon) { drawableToBitmap(task.icon) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = task.label,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(11.dp))
                    )
                } else {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = task.label,
                        tint = ElectricCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // App Label
            Text(
                text = task.label,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(3.dp))

            // RAM Usage in Electric Cyan
            Text(
                text = "${task.memoryUsageMb} MB",
                color = ElectricCyan,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RecentAppsEmptyState(isArabic: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = null,
                tint = KineticMint,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = if (isArabic) "لا توجد تطبيقات نشطة في الخلفية" else "No active background apps",
                color = MutedText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun getFallbackIcon(packageName: String, label: String): ImageVector {
    val lower = (packageName + label).lowercase()
    return when {
        lower.contains("bluetooth") || lower.contains("phone") || lower.contains("call") -> Icons.Default.Call
        lower.contains("radio") || lower.contains("fm") -> Icons.Default.Radio
        lower.contains("music") || lower.contains("spotify") || lower.contains("audio") -> Icons.Default.MusicNote
        lower.contains("video") || lower.contains("youtube") || lower.contains("vlc") -> Icons.Default.PlayCircle
        lower.contains("vehicle") || lower.contains("climate") || lower.contains("ac") -> Icons.Default.AcUnit
        lower.contains("setting") -> Icons.Default.Settings
        lower.contains("carplay") || lower.contains("carlife") || lower.contains("plink") -> Icons.Default.DirectionsCar
        lower.contains("dvr") || lower.contains("camera") || lower.contains("record") -> Icons.Default.Videocam
        lower.contains("map") || lower.contains("nav") -> Icons.Default.Navigation
        lower.contains("calc") -> Icons.Default.Calculate
        lower.contains("calendar") -> Icons.Default.CalendarToday
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
