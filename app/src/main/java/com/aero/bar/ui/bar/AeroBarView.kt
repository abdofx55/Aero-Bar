package com.aero.bar.ui.bar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aero.bar.domain.model.HvacTelemetryState
import com.aero.bar.ui.icons.AeroIcons
import com.aero.bar.ui.theme.AeroBarTheme
import com.aero.bar.ui.theme.ElectricCyan
import kotlin.math.abs

private val PureBlack = Color(0xFF000000)
private val CleanPillSurface = Color(0xFF0C1016)
private val InactiveIconTint = Color(0xFF94A3B8)
private val SubtleDividerColor = Color(0xFF141B24)
private val WarmOrangeGlow = Color(0xFFFF9F43)
private val SwipeCueInactive = Color(0xFF283446)

/**
 * Option #3 Single-Row 960x88px Tesla-Style AeroBar (10 Spacious Elements, Larger 30.dp Icons):
 * - Pure OLED Black (#000000) background with zero outlines/borders.
 * - Clean Temperature (`21°`) & Fan Speed (`[Fan] 3`) pills without `+` / `−` clutter on the bar.
 * - Tapping or dragging Temperature / Fan pills opens the minimal zero-text pop-up slider above the bar.
 * - Recents uses standard `.clickable` for 0ms instant single-tap response (double-tap handled in ViewModel).
 */
@Composable
fun AeroBarView(
    state: AeroBarState,
    onIntent: (AeroBarIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val hvac = state.hvacState
    val isHvacPowered = hvac.isAccOperational && hvac.isAcOn

    // Force LTR direction on the bar so it never inverts even if system locale is Arabic
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
    ) {
        if (state.isBarCollapsed) {
            // Minimal Collapsed Strip (16dp) with glowing center handle to tap to expand
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { onIntent(AeroBarIntent.OnToggleBarCollapse) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.6f))
                )
            }
        } else {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(PureBlack)
                    .padding(horizontal = 28.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (state.hvacState.isScreenStandby) {
                                    onIntent(AeroBarIntent.OnHomeClicked)
                                }
                            },
                            onDoubleTap = { onIntent(AeroBarIntent.OnToggleBarCollapse) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // =========================================================================
                    // 1. LEFT GROUP: System Navigation (Back, Home, Recents, All Apps)
                    // =========================================================================
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Back (38.dp icon with tactile press feedback; long-press collapses the dock)
                        AeroIconButton(
                            icon = AeroIcons.IcNavBack,
                            contentDescription = "Back",
                            isActive = false,
                            iconSize = 38.dp,
                            width = 68.dp,
                            onClick = { onIntent(AeroBarIntent.OnBackClicked) },
                            onLongClick = { onIntent(AeroBarIntent.OnToggleBarCollapse) }
                        )

                        // 2. Home (Remains ACTIVE while floating Recents / All Apps are open; only deselects when a full app is open)
                        AeroIconButton(
                            icon = AeroIcons.IcNavHome,
                            contentDescription = "Home",
                            isActive = !hvac.isClimatePanelOpen && !hvac.is360CameraActive,
                            iconSize = 38.dp,
                            width = 68.dp,
                            onClick = { onIntent(AeroBarIntent.OnHomeClicked) },
                            onLongClick = { onIntent(AeroBarIntent.OnHomeLongClicked) }
                        )

                        // 3. Recents (0ms instant single-tap response; rapid double-tap handled in ViewModel)
                        AeroIconButton(
                            icon = AeroIcons.IcNavRecents,
                            contentDescription = "Recent Apps",
                            isActive = state.isRecentsOpen,
                            iconSize = 38.dp,
                            width = 68.dp,
                            onClick = { onIntent(AeroBarIntent.OnRecentsClicked) }
                        )

                        // 4. All Apps Drawer
                        AeroIconButton(
                            icon = AeroIcons.IcNavAllApps,
                            contentDescription = "All Apps",
                            isActive = state.isDrawerOpen,
                            iconSize = 38.dp,
                            width = 68.dp,
                            onClick = { onIntent(AeroBarIntent.OnDrawerToggleClicked) }
                        )
                    }

                    BarSubtleDivider()

                    // =========================================================================
                    // 2. 360° AVM CAMERA BUTTON (Icon B: Circular Arrow Ring around "360")
                    // =========================================================================
                    AeroCamera360Button(
                        isActive = hvac.is360CameraActive,
                        onClick = { onIntent(AeroBarIntent.On360CameraClicked) }
                    )

                    BarSubtleDivider()

                    // =========================================================================
                    // 3. CENTER GROUP: AC (Long-press = AUTO), CLEAN TEMP PILL, CLEAN FAN PILL
                    // =========================================================================
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 6. AC Power Switch (Single tap = AC Toggle, Long press = AUTO Mode)
                        AeroAcToggleButton(
                            isAcOn = hvac.isAcOn,
                            isAutoMode = hvac.isAutoMode,
                            onClick = { onIntent(AeroBarIntent.OnAcToggleClicked) },
                            onLongClick = { onIntent(AeroBarIntent.OnAutoToggleClicked) }
                        )

                        // 7. Clean Temperature Pill (21°) — No + / - clutter on the bar
                        CleanTemperaturePill(
                            tempCelsius = hvac.temperatureC,
                            displayText = hvac.formattedTemperature,
                            isHvacPowered = isHvacPowered,
                            isSliderOpen = state.activeHvacSlider == ActiveHvacSlider.TEMPERATURE,
                            onClick = { onIntent(AeroBarIntent.OnTempPillClicked) },
                            onStepDelta = { delta -> onIntent(AeroBarIntent.OnTemperatureStep(delta)) }
                        )

                        // 8. Clean Fan Speed Pill ([Fan] 3) — No + / - clutter on the bar
                        CleanFanSpeedPill(
                            windLevel = hvac.fanSpeed,
                            displayText = hvac.formattedFanSpeed,
                            isHvacPowered = isHvacPowered,
                            isSliderOpen = state.activeHvacSlider == ActiveHvacSlider.FAN_SPEED,
                            onClick = { onIntent(AeroBarIntent.OnFanPillClicked) },
                            onStepDelta = { delta -> onIntent(AeroBarIntent.OnFanSpeedStep(delta)) }
                        )
                    }

                    BarSubtleDivider()

                    // =========================================================================
                    // 4. RIGHT GROUP: Climate/Seats App, Front Defrost, Air Recirculation (Larger 40.dp Icons)
                    // =========================================================================
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 9. Full Climate & Seat Heating Control App (Larger 40.dp)
                        AeroIconButton(
                            icon = AeroIcons.IcClimateSeatWaves,
                            contentDescription = "Climate & Seats App",
                            isActive = hvac.isClimatePanelOpen,
                            activeColor = ElectricCyan,
                            iconSize = 40.dp,
                            width = 68.dp,
                            onClick = { onIntent(AeroBarIntent.OnClimateSeatAppClicked) }
                        )

                        // 10. Front Windshield Defrost (Larger 40.dp)
                        AeroIconButton(
                            icon = AeroIcons.IcWindshieldDefrost,
                            contentDescription = "Front Defrost",
                            isActive = hvac.isFrontDefrostOn,
                            activeColor = WarmOrangeGlow,
                            iconSize = 40.dp,
                            width = 68.dp,
                            onClick = { onIntent(AeroBarIntent.OnFrontDefrostClicked) }
                        )

                        // 11. Cabin Air Recirculation (Exact OEM Option 2 Silhouette, Larger 40.dp)
                        AeroIconButton(
                            icon = AeroIcons.IcCarRecirculation,
                            contentDescription = "Air Recirculation",
                            isActive = hvac.isInternalRecirculation,
                            activeColor = ElectricCyan,
                            iconSize = 40.dp,
                            width = 68.dp,
                            onClick = { onIntent(AeroBarIntent.OnRecirculationClicked) }
                        )
                    }
                }
            }
    }
}
}

/**
 * Subtle vertical separator between functional clusters (no outlines on buttons).
 */
@Composable
private fun BarSubtleDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(30.dp)
            .background(SubtleDividerColor)
    )
}

/**
 * Design #5 Selection Style Touch Button with larger 30.dp icon and 58.dp touch width:
 * - Uses standard `.clickable` when `onLongClick == null` so single-tap fires in 0ms.
 * - Uses `.combinedClickable` only when `onLongClick` is provided (without `onDoubleClick`, so `onClick` still fires in 0ms!).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AeroIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    activeColor: Color = ElectricCyan,
    width: Dp = 68.dp,
    iconSize: Dp = 38.dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1.0f,
        animationSpec = tween(durationMillis = 90),
        label = "pressScale"
    )

    val animatedTint by animateColorAsState(
        targetValue = when {
            isPressed -> Color.White
            isActive -> activeColor
            else -> InactiveIconTint
        },
        animationSpec = tween(durationMillis = 140),
        label = "iconTint"
    )

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(62.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(16.dp))
            .then(
                when {
                    isPressed -> Modifier.background(Color.White.copy(alpha = 0.16f))
                    isActive -> Modifier.background(Color(0xFF121822))
                    else -> Modifier
                }
            )
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = animatedTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * 360° AVM Camera Button — Icon B:
 * Circular Arrow Ring wrapping AROUND "360" text in the center with tactile feedback.
 */
@Composable
private fun AeroCamera360Button(
    isActive: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1.0f,
        animationSpec = tween(durationMillis = 90),
        label = "cam360Scale"
    )

    val animatedTint by animateColorAsState(
        targetValue = when {
            isPressed -> Color.White
            isActive -> ElectricCyan
            else -> InactiveIconTint
        },
        animationSpec = tween(durationMillis = 140),
        label = "cam360Tint"
    )

    Box(
        modifier = Modifier
            .width(68.dp)
            .height(62.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(16.dp))
            .then(
                when {
                    isPressed -> Modifier.background(Color.White.copy(alpha = 0.16f))
                    isActive -> Modifier.background(Color(0xFF121822))
                    else -> Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AeroIcons.IcCamera360Orbit,
                contentDescription = "360 Camera",
                tint = animatedTint,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = "360",
                color = animatedTint,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.4).sp
            )
        }
    }
}

/**
 * Bold Typography Toggle Button for "AC" / "AUTO" (Single-tap = AC Power, Long-press = AUTO Mode).
 * Fixed 76.dp width so switching between "AC" and "AUTO" produces zero layout resizing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AeroAcToggleButton(
    isAcOn: Boolean,
    isAutoMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1.0f,
        animationSpec = tween(durationMillis = 90),
        label = "acScale"
    )

    val isAutoActive = isAcOn && isAutoMode
    val displayText = if (isAutoActive) "AUTO" else "AC"

    val animatedColor by animateColorAsState(
        targetValue = when {
            isPressed -> Color.White
            isAutoActive -> WarmOrangeGlow
            isAcOn -> ElectricCyan
            else -> InactiveIconTint
        },
        animationSpec = tween(durationMillis = 140),
        label = "acToggleColor"
    )

    Box(
        modifier = Modifier
            .width(76.dp)
            .height(62.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(16.dp))
            .then(
                when {
                    isPressed -> Modifier.background(Color.White.copy(alpha = 0.16f))
                    isAcOn -> Modifier.background(Color(0xFF121822))
                    else -> Modifier
                }
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = animatedColor,
            fontSize = if (isAutoActive) 17.sp else 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = if (isAutoActive) 0.8.sp else 0.5.sp
        )
    }
}

/**
 * Clean Temperature Pill (`21°`) without `+` or `−` clutter:
 * - Tapping opens the Minimal Design #2 Temperature Ruler Slider popup.
 * - Dragging horizontally scrubs temperature with calibrated driving sensitivity (14.dp dead-zone + 32.dp per 1°C).
 */
@Composable
private fun CleanTemperaturePill(
    tempCelsius: Int,
    displayText: String,
    isHvacPowered: Boolean,
    isSliderOpen: Boolean,
    onClick: () -> Unit,
    onStepDelta: (Int) -> Unit
) {
    val thermalColor = when {
        !isHvacPowered || displayText == "---" -> InactiveIconTint
        tempCelsius <= 21 -> ElectricCyan
        tempCelsius in 22..25 -> Color.White
        else -> WarmOrangeGlow
    }
    val animatedTempColor by animateColorAsState(
        targetValue = thermalColor,
        animationSpec = tween(durationMillis = 200),
        label = "thermalColor"
    )
    val cueColor by animateColorAsState(
        targetValue = if (isSliderOpen) thermalColor else SwipeCueInactive,
        animationSpec = tween(durationMillis = 180),
        label = "tempCueColor"
    )

    val density = LocalDensity.current
    val deadZonePx = remember(density) { with(density) { 14.dp.toPx() } }
    val stepThresholdPx = remember(density) { with(density) { 32.dp.toPx() } }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var deadZonePassed by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .width(114.dp)
            .height(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CleanPillSurface)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragAccumulator = 0f
                        deadZonePassed = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (abs(deadZonePassed) < deadZonePx) {
                            deadZonePassed += dragAmount
                            return@detectHorizontalDragGestures
                        }
                        dragAccumulator += dragAmount
                        while (dragAccumulator >= stepThresholdPx) {
                            onStepDelta(+1)
                            dragAccumulator -= stepThresholdPx
                        }
                        while (dragAccumulator <= -stepThresholdPx) {
                            onStepDelta(-1)
                            dragAccumulator += stepThresholdPx
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = animatedTempColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
        )

        // Subtle bottom swipe cue bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-5).dp)
                .width(36.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(cueColor)
        )
    }
}

/**
 * Clean Fan Speed Pill (`[Fan] 3`) without `+` or `−` clutter:
 * - Tapping opens the Minimal Design #3 Segmented Fan Speed Slider popup.
 * - Dragging horizontally scrubs fan speed with calibrated driving sensitivity (14.dp dead-zone + 36.dp per step).
 */
@Composable
private fun CleanFanSpeedPill(
    windLevel: Int,
    displayText: String,
    isHvacPowered: Boolean,
    isSliderOpen: Boolean,
    onClick: () -> Unit,
    onStepDelta: (Int) -> Unit
) {
    val activeTint = if (isHvacPowered && windLevel > 0) ElectricCyan else InactiveIconTint
    val cueColor by animateColorAsState(
        targetValue = if (isSliderOpen) ElectricCyan else SwipeCueInactive,
        animationSpec = tween(durationMillis = 180),
        label = "fanCueColor"
    )

    val density = LocalDensity.current
    val deadZonePx = remember(density) { with(density) { 14.dp.toPx() } }
    val stepThresholdPx = remember(density) { with(density) { 36.dp.toPx() } }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var deadZonePassed by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .width(114.dp)
            .height(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CleanPillSurface)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragAccumulator = 0f
                        deadZonePassed = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (abs(deadZonePassed) < deadZonePx) {
                            deadZonePassed += dragAmount
                            return@detectHorizontalDragGestures
                        }
                        dragAccumulator += dragAmount
                        while (dragAccumulator >= stepThresholdPx) {
                            onStepDelta(+1)
                            dragAccumulator -= stepThresholdPx
                        }
                        while (dragAccumulator <= -stepThresholdPx) {
                            onStepDelta(-1)
                            dragAccumulator += stepThresholdPx
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
        ) {
            Icon(
                imageVector = AeroIcons.IcFan4Blade,
                contentDescription = "Fan Speed",
                tint = activeTint,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = displayText,
                color = if (isHvacPowered && windLevel > 0) Color.White else InactiveIconTint,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Subtle bottom swipe cue bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-5).dp)
                .width(36.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(cueColor)
        )
    }
}

@Preview(
    name = "AeroBar Option #3 (10 Elements, Larger 30.dp Icons, Clean Pills)",
    widthDp = 960,
    heightDp = 88,
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
fun AeroBarViewPreview() {
    AeroBarTheme {
        AeroBarView(
            state = AeroBarState(
                isDrawerOpen = false,
                isRecentsOpen = false,
                hvacState = HvacTelemetryState(
                    isAccOperational = true,
                    isAcOn = true,
                    isAutoMode = false,
                    isInternalRecirculation = true,
                    isFrontDefrostOn = false,
                    temperatureC = 21,
                    fanSpeed = 3
                )
            ),
            onIntent = {}
        )
    }
}
