package com.aero.bar.domain.model

/**
 * Immutable domain model representing the real-time state of the Dongfeng Shine (Aeolus D53)
 * HVAC climate system, 360° AVM camera, Reverse gear, and headunit display standby.
 */
data class HvacTelemetryState(
    val isHardwareConnected: Boolean = false,
    val isAccOperational: Boolean = true,
    val isAcOn: Boolean = true,
    val isAutoMode: Boolean = true,
    val temperatureC: Int = 21,
    val fanSpeed: Int = 3,
    val airSupplyMode: Int = 2,
    val isFrontDefrostOn: Boolean = false,
    val isInternalRecirculation: Boolean = true,
    val isClimatePanelOpen: Boolean = false,
    val is360CameraActive: Boolean = false,
    val isReverseGearActive: Boolean = false,
    val isScreenStandby: Boolean = false
) {
    companion object {
        const val MIN_TEMP_C = 16
        const val MAX_TEMP_C = 32
        const val MIN_FAN_SPEED = 1
        const val MAX_FAN_SPEED = 8
    }

    /**
     * Formatted temperature string matching vehicle CAN bus conventions:
     * - "---" when AC power switch is OFF
     * - "LO" at 16°C
     * - "HI" at 32°C
     * - "XX°" between 17°C and 31°C
     */
    val formattedTemperature: String
        get() = when {
            !isAcOn || !isAccOperational -> "---"
            temperatureC <= MIN_TEMP_C -> "LO"
            temperatureC >= MAX_TEMP_C -> "HI"
            else -> "${temperatureC}°"
        }

    /**
     * Formatted fan speed string ("1".."8", or "-" when AC is off).
     */
    val formattedFanSpeed: String
        get() = if (!isAcOn || !isAccOperational || fanSpeed <= 0) "-" else fanSpeed.coerceIn(MIN_FAN_SPEED, MAX_FAN_SPEED).toString()
}
