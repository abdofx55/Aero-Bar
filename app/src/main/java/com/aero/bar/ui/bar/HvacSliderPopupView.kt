package com.aero.bar.ui.bar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aero.bar.domain.model.HvacTelemetryState
import com.aero.bar.ui.icons.AeroIcons
import com.aero.bar.ui.theme.AeroBlack
import com.aero.bar.ui.theme.ElectricCyan
import com.aero.bar.ui.theme.ObsidianCard
import com.aero.bar.ui.theme.ObsidianCardElevated
import com.aero.bar.ui.theme.TextMuted
import com.aero.bar.ui.theme.TextPrimary
import com.aero.bar.ui.theme.TextSecondary
import kotlin.math.abs

private val WarmOrangeGlow = Color(0xFFFF9F43)
private val ActiveCoolPillBg = Color(0x2600D2FF)
private val ActiveWarmPillBg = Color(0x26FF9F43)

/**
 * Minimal, zero-text floating slider popups for Temperature (Design #2: Numeric Ruler with - / +)
 * and Fan Speed (Design #3: 8 Luminous Segments with - / +).
 *
 * Calibrated driving sensitivity:
 * - Initial road-bump dead-zone: 14.dp
 * - Temperature step threshold: 32.dp per 1°C
 * - Fan speed step threshold: 36.dp per 1 level
 */
@Composable
fun HvacSliderPopupView(
    sliderType: ActiveHvacSlider,
    hvacState: HvacTelemetryState,
    onIntent: (AeroBarIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    when (sliderType) {
        ActiveHvacSlider.TEMPERATURE -> {
            TemperatureRulerSliderPopup(
                tempC = hvacState.temperatureC,
                onStep = { delta -> onIntent(AeroBarIntent.OnTemperatureStep(delta)) },
                onSelectExact = { exact -> onIntent(AeroBarIntent.OnTemperatureSetExact(exact)) },
                modifier = modifier
            )
        }
        ActiveHvacSlider.FAN_SPEED -> {
            FanSegmentedSliderPopup(
                fanSpeed = hvacState.fanSpeed,
                onStep = { delta -> onIntent(AeroBarIntent.OnFanSpeedStep(delta)) },
                onSelectExact = { exact -> onIntent(AeroBarIntent.OnFanSpeedSetExact(exact)) },
                modifier = modifier
            )
        }
        ActiveHvacSlider.NONE -> Unit
    }
}

/**
 * Design #2: Minimal Tesla Numeric Ruler for Temperature.
 * Zero text headers or labels — pure numbers + left [−] and right [+] buttons + 5.dp bottom thermal progress bar.
 */
@Composable
private fun TemperatureRulerSliderPopup(
    tempC: Int,
    onStep: (Int) -> Unit,
    onSelectExact: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor by animateColorAsState(
        targetValue = when {
            tempC <= 21 -> ElectricCyan
            tempC in 22..25 -> Color.White
            else -> WarmOrangeGlow
        },
        animationSpec = tween(180),
        label = "TempSliderColor"
    )

    val activeBgColor by animateColorAsState(
        targetValue = when {
            tempC <= 21 -> ActiveCoolPillBg
            tempC in 22..25 -> ObsidianCardElevated
            else -> ActiveWarmPillBg
        },
        animationSpec = tween(180),
        label = "TempSliderBgColor"
    )

    val density = LocalDensity.current
    val deadZonePx = remember(density) { with(density) { 14.dp.toPx() } }
    val stepThresholdPx = remember(density) { with(density) { 32.dp.toPx() } }
    var dragAccumulatorPx by remember { mutableFloatStateOf(0f) }
    var deadZonePassed by remember { mutableFloatStateOf(0f) }

    val progressFraction by animateFloatAsState(
        targetValue = ((tempC - HvacTelemetryState.MIN_TEMP_C).toFloat() /
            (HvacTelemetryState.MAX_TEMP_C - HvacTelemetryState.MIN_TEMP_C).toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(150),
        label = "TempProgressFraction"
    )

    Column(
        modifier = modifier
            .width(580.dp)
            .height(88.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF0C1016))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragAccumulatorPx = 0f
                        deadZonePassed = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (abs(deadZonePassed) < deadZonePx) {
                            deadZonePassed += dragAmount
                            return@detectHorizontalDragGestures
                        }
                        dragAccumulatorPx += dragAmount
                        while (dragAccumulatorPx >= stepThresholdPx) {
                            onStep(+1)
                            dragAccumulatorPx -= stepThresholdPx
                        }
                        while (dragAccumulatorPx <= -stepThresholdPx) {
                            onStep(-1)
                            dragAccumulatorPx += stepThresholdPx
                        }
                    }
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Minus [−] Button (Larger 54.dp touch target)
            SliderStepIconButton(
                icon = AeroIcons.IcMinus,
                contentDescription = "Decrease Temperature",
                onClick = { onStep(-1) }
            )

            // 7-point Horizontal Numeric Ruler: [temp-3 .. temp .. temp+3]
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (offset in -3..3) {
                    val candidateTemp = tempC + offset
                    val isCenter = offset == 0
                    val isWithinBounds = candidateTemp in HvacTelemetryState.MIN_TEMP_C..HvacTelemetryState.MAX_TEMP_C

                    Box(
                        modifier = Modifier
                            .width(if (isCenter) 70.dp else 48.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCenter) activeBgColor else Color.Transparent)
                            .clickable(
                                enabled = isWithinBounds,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (isWithinBounds) {
                                    onSelectExact(candidateTemp)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isWithinBounds) {
                            val label = when {
                                candidateTemp <= HvacTelemetryState.MIN_TEMP_C -> "LO"
                                candidateTemp >= HvacTelemetryState.MAX_TEMP_C -> "HI"
                                else -> "${candidateTemp}°"
                            }
                            val textColor = when {
                                isCenter -> activeColor
                                abs(offset) == 1 -> TextPrimary.copy(alpha = 0.75f)
                                abs(offset) == 2 -> TextSecondary.copy(alpha = 0.60f)
                                else -> TextMuted.copy(alpha = 0.55f)
                            }
                            Text(
                                text = label,
                                color = textColor,
                                fontSize = when {
                                    isCenter -> 26.sp
                                    abs(offset) == 1 -> 17.sp
                                    else -> 14.sp
                                },
                                fontWeight = if (isCenter) FontWeight.ExtraBold else FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Right Plus [+] Button (Larger 54.dp touch target)
            SliderStepIconButton(
                icon = AeroIcons.IcPlus,
                contentDescription = "Increase Temperature",
                onClick = { onStep(+1) }
            )
        }

        // Bottom 6.dp Thermal Progress Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(ObsidianCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction.coerceAtLeast(0.05f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(activeColor)
            )
        }
    }
}

/**
 * Design #3: Minimal Segmented Glowing Bar for Fan Speed.
 * Zero text headers or labels — pure 8 segments + left [−] and right [+] buttons.
 */
@Composable
private fun FanSegmentedSliderPopup(
    fanSpeed: Int,
    onStep: (Int) -> Unit,
    onSelectExact: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val deadZonePx = remember(density) { with(density) { 14.dp.toPx() } }
    val stepThresholdPx = remember(density) { with(density) { 36.dp.toPx() } }
    var dragAccumulatorPx by remember { mutableFloatStateOf(0f) }
    var deadZonePassed by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = modifier
            .width(540.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF0C1016))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragAccumulatorPx = 0f
                        deadZonePassed = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (abs(deadZonePassed) < deadZonePx) {
                            deadZonePassed += dragAmount
                            return@detectHorizontalDragGestures
                        }
                        dragAccumulatorPx += dragAmount
                        while (dragAccumulatorPx >= stepThresholdPx) {
                            onStep(+1)
                            dragAccumulatorPx -= stepThresholdPx
                        }
                        while (dragAccumulatorPx <= -stepThresholdPx) {
                            onStep(-1)
                            dragAccumulatorPx += stepThresholdPx
                        }
                    }
                )
            }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Minus [−] Button (Larger 54.dp touch target)
        SliderStepIconButton(
            icon = AeroIcons.IcMinus,
            contentDescription = "Decrease Fan Speed",
            onClick = { onStep(-1) }
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 8 Luminous Segments (Levels 1..8, Larger 46.dp height)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (level in 1..HvacTelemetryState.MAX_FAN_SPEED) {
                val isLit = level <= fanSpeed
                val segmentColor by animateColorAsState(
                    targetValue = if (isLit) ElectricCyan else ObsidianCard,
                    animationSpec = tween(140),
                    label = "FanSegmentColor_$level"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(segmentColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectExact(level) }
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Right Plus [+] Button (Larger 54.dp touch target)
        SliderStepIconButton(
            icon = AeroIcons.IcPlus,
            contentDescription = "Increase Fan Speed",
            onClick = { onStep(+1) }
        )
    }
}

@Composable
private fun SliderStepIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianCardElevated)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(26.dp)
        )
    }
}
