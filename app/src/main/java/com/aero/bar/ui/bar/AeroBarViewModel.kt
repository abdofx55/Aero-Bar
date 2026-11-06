package com.aero.bar.ui.bar

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aero.bar.domain.repository.AppRepository
import com.aero.bar.domain.repository.RecentAppsRepository
import com.aero.bar.domain.repository.VehicleHvacRepository
import com.aero.bar.service.AeroAccessibilityService
import com.aero.bar.ui.recents.RecentAppsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the Single-Row AeroBar (960x88px).
 * Coordinates Navigation, Zero-Latency Overlays, Pop-up HVAC Sliders (with 3s auto-hide),
 * and Hardware CAN-Bus commands.
 */
class AeroBarViewModel(
    private val appRepository: AppRepository,
    private val vehicleHvacRepository: VehicleHvacRepository,
    private val recentAppsRepository: RecentAppsRepository? = null,
    private val recentAppsViewModel: RecentAppsViewModel? = null,
    private val onOverlayResize: ((Boolean) -> Unit)? = null,
    private val onHomeAction: (() -> Unit)? = null,
    private val onBarCollapseChange: ((Boolean) -> Unit)? = null
) : ViewModel() {

    companion object {
        private const val TAG = "AeroBarViewModel"
        private const val SLIDER_AUTO_HIDE_MS = 3000L
        private const val DOUBLE_TAP_WINDOW_MS = 450L
        private const val OVERLAY_AUTO_DISMISS_SECONDS = 15
    }

    private val _uiState = MutableStateFlow(AeroBarState())
    val uiState: StateFlow<AeroBarState> = _uiState.asStateFlow()

    private var sliderAutoHideJob: Job? = null
    private var overlayCountdownJob: Job? = null
    private var lastRecentsTapElapsedMs: Long = 0L

    init {
        observePinnedApps()
        observeHvacTelemetry()
    }

    private fun observePinnedApps() {
        viewModelScope.launch {
            appRepository.getPinnedApps().collect { pinned ->
                _uiState.update { it.copy(pinnedApps = pinned) }
            }
        }
    }

    private fun observeHvacTelemetry() {
        viewModelScope.launch {
            vehicleHvacRepository.telemetryState.collect { telemetry ->
                val shouldCloseOverlays = telemetry.isReverseGearActive || telemetry.is360CameraActive
                if (shouldCloseOverlays && isAnyPopupOpen()) {
                    sliderAutoHideJob?.cancel()
                    cancelOverlayCountdown()
                    onOverlayResize?.invoke(false)
                }
                _uiState.update { current ->
                    current.copy(
                        hvacState = telemetry,
                        isRecentsOpen = if (shouldCloseOverlays) false else current.isRecentsOpen,
                        isDrawerOpen = if (shouldCloseOverlays) false else current.isDrawerOpen,
                        activeHvacSlider = if (shouldCloseOverlays) ActiveHvacSlider.NONE else current.activeHvacSlider
                    )
                }
            }
        }
    }

    private fun isAnyPopupOpen(state: AeroBarState = _uiState.value): Boolean {
        return state.isRecentsOpen || state.isDrawerOpen || state.activeHvacSlider != ActiveHvacSlider.NONE
    }

    private fun scheduleSliderAutoHide() {
        sliderAutoHideJob?.cancel()
        sliderAutoHideJob = viewModelScope.launch {
            delay(SLIDER_AUTO_HIDE_MS)
            if (_uiState.value.activeHvacSlider != ActiveHvacSlider.NONE) {
                _uiState.update { it.copy(activeHvacSlider = ActiveHvacSlider.NONE) }
                onOverlayResize?.invoke(isAnyPopupOpen())
            }
        }
    }

    private fun startOverlayCountdown() {
        overlayCountdownJob?.cancel()
        _uiState.update { it.copy(overlayDismissCountdown = OVERLAY_AUTO_DISMISS_SECONDS) }
        overlayCountdownJob = viewModelScope.launch {
            for (sec in (OVERLAY_AUTO_DISMISS_SECONDS - 1) downTo 0) {
                delay(1000L)
                if (!_uiState.value.isDrawerOpen && !_uiState.value.isRecentsOpen) break
                _uiState.update { it.copy(overlayDismissCountdown = sec) }
                if (sec == 0) {
                    closeOverlays()
                    break
                }
            }
        }
    }

    private fun cancelOverlayCountdown() {
        overlayCountdownJob?.cancel()
        _uiState.update { it.copy(overlayDismissCountdown = OVERLAY_AUTO_DISMISS_SECONDS) }
    }

    private fun openHvacSlider(slider: ActiveHvacSlider) {
        vehicleHvacRepository.dismissVehiclePanels()
        cancelOverlayCountdown()
        _uiState.update {
            it.copy(
                activeHvacSlider = slider,
                isRecentsOpen = false,
                isDrawerOpen = false
            )
        }
        onOverlayResize?.invoke(true)
        scheduleSliderAutoHide()
    }

    fun closeOverlays() {
        sliderAutoHideJob?.cancel()
        cancelOverlayCountdown()
        if (isAnyPopupOpen()) {
            _uiState.update {
                it.copy(
                    isDrawerOpen = false,
                    isRecentsOpen = false,
                    activeHvacSlider = ActiveHvacSlider.NONE
                )
            }
            onOverlayResize?.invoke(false)
        }
    }

    fun wakeScreenIfOff(): Boolean {
        return vehicleHvacRepository.wakeScreenIfOff()
    }

    fun onIntent(intent: AeroBarIntent) {
        Log.i(TAG, "AeroBar Intent received: $intent | current state: recents=${_uiState.value.isRecentsOpen}, drawer=${_uiState.value.isDrawerOpen}, slider=${_uiState.value.activeHvacSlider}")
        when (intent) {
            is AeroBarIntent.OnBackClicked -> {
                if (vehicleHvacRepository.wakeScreenIfOff()) return
                if (isAnyPopupOpen()) {
                    closeOverlays()
                } else if (_uiState.value.hvacState.isClimatePanelOpen || _uiState.value.hvacState.is360CameraActive) {
                    vehicleHvacRepository.dismissVehiclePanels()
                    AeroAccessibilityService.performBack()
                } else {
                    AeroAccessibilityService.performBack()
                }
            }

            is AeroBarIntent.OnHomeClicked -> {
                if (vehicleHvacRepository.wakeScreenIfOff()) return
                closeOverlays()
                vehicleHvacRepository.dismissVehiclePanels()
                onHomeAction?.invoke() ?: AeroAccessibilityService.performHome()
            }

            is AeroBarIntent.OnHomeLongClicked -> {
                if (vehicleHvacRepository.wakeScreenIfOff()) return
                closeOverlays()
                vehicleHvacRepository.dismissVehiclePanels()
                vehicleHvacRepository.enterScreenStandby()
            }

            is AeroBarIntent.OnRecentsClicked -> {
                if (vehicleHvacRepository.wakeScreenIfOff()) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastRecentsTapElapsedMs <= DOUBLE_TAP_WINDOW_MS) {
                    // Instant Double-Tap detected -> Quick Switch (Alt+Tab)
                    lastRecentsTapElapsedMs = 0L
                    closeOverlays()
                    vehicleHvacRepository.dismissVehiclePanels()
                    viewModelScope.launch {
                        recentAppsRepository?.quickSwitchLastApp()
                    }
                    return
                }
                lastRecentsTapElapsedMs = now

                sliderAutoHideJob?.cancel()
                val willOpen = !_uiState.value.isRecentsOpen
                if (willOpen) {
                    vehicleHvacRepository.dismissVehiclePanels()
                    recentAppsViewModel?.loadRecents()
                    startOverlayCountdown()
                } else {
                    cancelOverlayCountdown()
                }
                _uiState.update {
                    it.copy(
                        isRecentsOpen = willOpen,
                        isDrawerOpen = false,
                        activeHvacSlider = ActiveHvacSlider.NONE
                    )
                }
                onOverlayResize?.invoke(willOpen)
            }

            is AeroBarIntent.OnRecentsDoubleClicked -> {
                if (vehicleHvacRepository.wakeScreenIfOff()) return
                closeOverlays()
                vehicleHvacRepository.dismissVehiclePanels()
                viewModelScope.launch {
                    recentAppsRepository?.quickSwitchLastApp()
                }
            }

            is AeroBarIntent.OnDrawerToggleClicked -> {
                if (vehicleHvacRepository.wakeScreenIfOff()) return
                sliderAutoHideJob?.cancel()
                val willOpen = !_uiState.value.isDrawerOpen
                if (willOpen) {
                    vehicleHvacRepository.dismissVehiclePanels()
                    startOverlayCountdown()
                } else {
                    cancelOverlayCountdown()
                }
                _uiState.update {
                    it.copy(
                        isDrawerOpen = willOpen,
                        isRecentsOpen = false,
                        activeHvacSlider = ActiveHvacSlider.NONE
                    )
                }
                onOverlayResize?.invoke(willOpen)
            }

            is AeroBarIntent.OnResetOverlayTimer -> {
                if (_uiState.value.isDrawerOpen || _uiState.value.isRecentsOpen) {
                    startOverlayCountdown()
                }
            }

            is AeroBarIntent.OnToggleBarCollapse -> {
                val newCollapsed = !_uiState.value.isBarCollapsed
                if (newCollapsed) {
                    closeOverlays()
                }
                _uiState.update { it.copy(isBarCollapsed = newCollapsed) }
                onBarCollapseChange?.invoke(newCollapsed)
            }

            is AeroBarIntent.On360CameraClicked -> {
                closeOverlays()
                val isCurrentlyActive = _uiState.value.hvacState.is360CameraActive
                if (isCurrentlyActive) {
                    vehicleHvacRepository.dismissVehiclePanels()
                    AeroAccessibilityService.performBack()
                } else {
                    vehicleHvacRepository.toggle360Camera()
                }
            }

            is AeroBarIntent.OnAcToggleClicked -> {
                vehicleHvacRepository.toggleAcPower()
            }

            is AeroBarIntent.OnAutoToggleClicked -> {
                vehicleHvacRepository.toggleAutoMode()
            }

            is AeroBarIntent.OnTempPillClicked -> {
                if (vehicleHvacRepository.wakeScreenIfOff()) return
                val current = _uiState.value.activeHvacSlider
                if (current == ActiveHvacSlider.TEMPERATURE) {
                    closeOverlays()
                } else {
                    openHvacSlider(ActiveHvacSlider.TEMPERATURE)
                }
            }

            is AeroBarIntent.OnFanPillClicked -> {
                if (vehicleHvacRepository.wakeScreenIfOff()) return
                val current = _uiState.value.activeHvacSlider
                if (current == ActiveHvacSlider.FAN_SPEED) {
                    closeOverlays()
                } else {
                    openHvacSlider(ActiveHvacSlider.FAN_SPEED)
                }
            }

            is AeroBarIntent.OnDismissHvacSlider -> {
                if (_uiState.value.activeHvacSlider != ActiveHvacSlider.NONE) {
                    sliderAutoHideJob?.cancel()
                    _uiState.update { it.copy(activeHvacSlider = ActiveHvacSlider.NONE) }
                    onOverlayResize?.invoke(isAnyPopupOpen())
                }
            }

            is AeroBarIntent.OnTemperatureStep -> {
                openHvacSlider(ActiveHvacSlider.TEMPERATURE)
                vehicleHvacRepository.stepTemperature(intent.deltaSteps)
            }

            is AeroBarIntent.OnTemperatureSetExact -> {
                openHvacSlider(ActiveHvacSlider.TEMPERATURE)
                val delta = intent.targetTempC - _uiState.value.hvacState.temperatureC
                if (delta != 0) {
                    vehicleHvacRepository.stepTemperature(delta)
                }
            }

            is AeroBarIntent.OnFanSpeedStep -> {
                openHvacSlider(ActiveHvacSlider.FAN_SPEED)
                vehicleHvacRepository.stepFanSpeed(intent.deltaSteps)
            }

            is AeroBarIntent.OnFanSpeedSetExact -> {
                openHvacSlider(ActiveHvacSlider.FAN_SPEED)
                val delta = intent.targetSpeed - _uiState.value.hvacState.fanSpeed
                if (delta != 0) {
                    vehicleHvacRepository.stepFanSpeed(delta)
                }
            }

            is AeroBarIntent.OnClimateSeatAppClicked -> {
                closeOverlays()
                val isCurrentlyActive = _uiState.value.hvacState.isClimatePanelOpen
                if (isCurrentlyActive) {
                    vehicleHvacRepository.dismissVehiclePanels()
                    AeroAccessibilityService.performBack()
                } else {
                    vehicleHvacRepository.toggleClimateAndSeatsApp()
                }
            }

            is AeroBarIntent.OnFrontDefrostClicked -> {
                vehicleHvacRepository.toggleFrontDefrost()
            }

            is AeroBarIntent.OnRecirculationClicked -> {
                vehicleHvacRepository.toggleAirRecirculation()
            }

            is AeroBarIntent.OnStandbyClicked -> {
                closeOverlays()
                vehicleHvacRepository.dismissVehiclePanels()
                vehicleHvacRepository.enterScreenStandby()
            }

            is AeroBarIntent.OnPinnedAppClicked -> {
                viewModelScope.launch {
                    appRepository.launchApp(intent.app.packageName, intent.app.activityName)
                    closeOverlays()
                    vehicleHvacRepository.dismissVehiclePanels()
                }
            }

            is AeroBarIntent.OnPinnedAppLongClicked -> {}
        }
    }
}
