package com.aero.bar.ui.bar

import com.aero.bar.domain.model.AppItem
import com.aero.bar.domain.model.HvacTelemetryState

enum class ActiveHvacSlider {
    NONE,
    TEMPERATURE,
    FAN_SPEED
}

/**
 * Immutable MVI State for the Single-Row AeroBar (960x88px).
 */
data class AeroBarState(
    val hvacState: HvacTelemetryState = HvacTelemetryState(),
    val pinnedApps: List<AppItem> = emptyList(),
    val isDrawerOpen: Boolean = false,
    val isRecentsOpen: Boolean = false,
    val activeHvacSlider: ActiveHvacSlider = ActiveHvacSlider.NONE,
    val isCarParked: Boolean = true,
    val overlayDismissCountdown: Int = 15,
    val isBarCollapsed: Boolean = false
)

/**
 * Unidirectional MVI User Intents for the 10-Element Single-Row AeroBar & Pop-up Sliders.
 */
sealed interface AeroBarIntent {
    // Navigation & System Overlays
    data object OnBackClicked : AeroBarIntent
    data object OnHomeClicked : AeroBarIntent
    data object OnHomeLongClicked : AeroBarIntent
    data object OnRecentsClicked : AeroBarIntent
    data object OnRecentsDoubleClicked : AeroBarIntent
    data object OnDrawerToggleClicked : AeroBarIntent
    data object OnResetOverlayTimer : AeroBarIntent
    data object OnToggleBarCollapse : AeroBarIntent

    // 360° Around View Camera
    data object On360CameraClicked : AeroBarIntent

    // HVAC Core Controls & Pop-up Sliders
    data object OnAcToggleClicked : AeroBarIntent
    data object OnAutoToggleClicked : AeroBarIntent
    data object OnTempPillClicked : AeroBarIntent
    data object OnFanPillClicked : AeroBarIntent
    data object OnDismissHvacSlider : AeroBarIntent
    data class OnTemperatureStep(val deltaSteps: Int) : AeroBarIntent
    data class OnTemperatureSetExact(val targetTempC: Int) : AeroBarIntent
    data class OnFanSpeedStep(val deltaSteps: Int) : AeroBarIntent
    data class OnFanSpeedSetExact(val targetSpeed: Int) : AeroBarIntent

    // Vehicle Climate/Seat App, Defrost, Recirculation & Display Standby
    data object OnClimateSeatAppClicked : AeroBarIntent
    data object OnFrontDefrostClicked : AeroBarIntent
    data object OnRecirculationClicked : AeroBarIntent
    data object OnStandbyClicked : AeroBarIntent

    // Legacy Pinned Apps Support
    data class OnPinnedAppClicked(val app: AppItem) : AeroBarIntent
    data class OnPinnedAppLongClicked(val app: AppItem) : AeroBarIntent
}
