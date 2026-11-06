package com.aero.bar.domain.repository

import com.aero.bar.domain.model.HvacTelemetryState
import kotlinx.coroutines.flow.StateFlow

/**
 * Clean Architecture Domain Repository Interface for Dongfeng Shine Vehicle & HVAC Hardware.
 * Adheres to Dependency Inversion (DIP) and Interface Segregation (ISP):
 * - On the real headunit, communicates via Binder with Qinggan CAN Bus (`qg.canbus`),
 *   VehicleService (`vehicle.service`), SystemPolicy/ReverseManager (`qg.system_policy`),
 *   and PowerManager (`qg.power_manager`).
 * - On an emulator/development environment, provides reactive simulated state transitions.
 */
interface VehicleHvacRepository {

    /**
     * Real-time reactive stream of HVAC & Vehicle hardware telemetry.
     */
    val telemetryState: StateFlow<HvacTelemetryState>

    /**
     * Toggles the main A/C power switch (`openAirConditionSwitch` / `closeAirConditionSwitch`).
     */
    fun toggleAcPower()

    /**
     * Toggles automatic climate control (`openAirAutoMode` / `closeAirAutoMode`).
     */
    fun toggleAutoMode()

    /**
     * Steps the cabin target temperature up (+1°C) or down (-1°C) within [16..32].
     */
    fun stepTemperature(deltaSteps: Int)

    /**
     * Steps the cabin blower fan speed up (+1) or down (-1) within [1..8].
     */
    fun stepFanSpeed(deltaSteps: Int)

    /**
     * Opens or closes the full Dongfeng Vehicle Climate, Seat Heating & Airflow Distribution app
     * (`com.qinggan.app.vehicle.MainActivity` / `VehicleManager.closeVehicle()`).
     */
    fun toggleClimateAndSeatsApp()

    /**
     * Toggles front windshield defogger/defrost (`openAirFrontWindowDefogger` / `closeAirFrontWindowDefogger`).
     */
    fun toggleFrontDefrost()

    /**
     * Switches between internal cabin air recirculation and outside fresh air (`switchAirCirculationMode`).
     */
    fun toggleAirRecirculation()

    /**
     * Opens or closes the 360° Around View Monitor (AVM) camera (`ReverseManager.showAVM(1)` / `showAVM(0)`).
     */
    fun toggle360Camera()

    /**
     * Turns off the screen backlight / enters standby clock mode (`PowerManager.enterStandby()`).
     */
    fun enterScreenStandby()

    /**
     * Wakes the screen if currently in screen-off state (`PowerManager.exitScreenOff()`).
     * Returns true if the screen was off and consumed the touch to wake up.
     */
    fun wakeScreenIfOff(): Boolean

    /**
     * Dismisses and unselects any active vehicle panel/screen (`Climate & Seats App` or `360° Camera`)
     * when the user navigates to Home, Back, Recents, All Apps, or launches another application.
     */
    fun dismissVehiclePanels()
}
