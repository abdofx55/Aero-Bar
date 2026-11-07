package com.aero.bar.ui.drawer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aero.bar.domain.model.AppItem
import com.aero.bar.ui.icons.AeroIcons
import com.aero.bar.ui.theme.ElectricCyan

private val MenuCardBg = Color(0xFF131720)
private val ActionTileBg = Color(0xFF1C222D)
private val CloseBtnBg = Color(0xFF222B38)
private val MutedText = Color(0xFF94A3B8)
private val DividerColor = Color(0xFF1E2633)
private val DeleteRed = Color(0xFFFB7185)

/**
 * Android Standard App Long-Press Context Menu:
 * Floating card with App Icon + Label + Package Name,
 * App Info action, and direct Uninstall prompt for non-system apps.
 */
@Composable
fun AppContextMenu(
    app: AppItem,
    isArabic: Boolean,
    onDetails: () -> Unit,
    onHide: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(280.dp)
            .shadow(
                elevation = 28.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = Color.Black,
                ambientColor = Color.Black
            )
            .clip(RoundedCornerShape(22.dp))
            .background(MenuCardBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // Prevent tap from passing through
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: App Icon + Name & Package
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bitmap = remember(app.icon) { drawableToBitmap(app.icon) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ActionTileBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = app.label,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = app.label,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        color = MutedText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerColor)
            )

            // Action Item 1: App Info (معلومات التطبيق)
            ContextMenuActionItem(
                icon = Icons.Outlined.Info,
                label = if (isArabic) "معلومات التطبيق" else "App info",
                iconTint = ElectricCyan,
                textColor = Color.White,
                onClick = onDetails
            )

            // Action Item 2: Hide or Unhide App (إخفاء / إظهار التطبيق)
            ContextMenuActionItem(
                icon = if (app.isHidden) AeroIcons.IcEye else AeroIcons.IcEyeOff,
                label = if (app.isHidden) {
                    if (isArabic) "إظهار في القائمة" else "Unhide App"
                } else {
                    if (isArabic) "إخفاء التطبيق" else "Hide App"
                },
                iconTint = if (app.isHidden) Color(0xFF10B981) else Color(0xFFF59E0B),
                textColor = Color.White,
                onClick = onHide
            )

            // Action Item 3: Direct Uninstall (إلغاء التثبيت) - Shown only if non-system app
            if (app.canUninstall) {
                ContextMenuActionItem(
                    icon = Icons.Outlined.Delete,
                    label = if (isArabic) "إلغاء التثبيت" else "Uninstall",
                    iconTint = DeleteRed,
                    textColor = DeleteRed,
                    onClick = onUninstall
                )
            }
        }
    }
}

@Composable
private fun ContextMenuActionItem(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = 90),
        label = "actionItemScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPressed) ActionTileBg.copy(alpha = 0.6f) else ActionTileBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
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
