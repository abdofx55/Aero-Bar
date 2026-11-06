package com.qinggan.canbus

import android.os.Parcel
import android.os.Parcelable

/**
 * Binary-compatible Parcelable implementation of `com.qinggan.canbus.AirCondition`
 * matching the exact 61-field serialization order of the Dongfeng Shine (Aeolus D53) CAN Bus service.
 */
class AirCondition() : Parcelable {
    var airSWStatus: Int = -1
    var airACStatus: Int = -1
    var airHighWindStatus: Int = -1
    var airLowWindStatus: Int = -1
    var airDUALStatus: Int = -1
    var airMaxFrontStatus: Int = -1
    var airRearLightStatus: Int = -1
    var airSupplyStatus: Int = -1
    var airDisplaySW: Int = -1
    var airWindSpeed: Int = -1
    var airLeftTemperature: Float = -1.0f
    var airRightTemperature: Float = -1.0f
    var airRearTemperature: Float = -1.0f
    var airCirculationMode: Int = -1
    var airLeftSeatHeatingLevel: Int = -1
    var airRearCtlLockSW: Int = -1
    var airACMaxSW: Int = -1
    var airRightSeatHeatingLevel: Int = -1
    var airRearWindowHeatingStatus: Int = -1
    var airECO: Int = -1
    var airFrontWindowDefogger: Int = -1
    var airMode: Int = -1
    var airCirculationStatus: Int = -1
    var airRearWindSpeed: Int = -1
    var airRearDownSupplyStatus: Int = -1
    var airRearParallelSupplyStatus: Int = -1
    var airRearUpSupplyStatus: Int = -1
    var airRearWindowDefogger: Int = -1
    var airLeftMirrorDefogger: Int = -1
    var airRightMirrorDefogger: Int = -1
    var airSyncStatus: Int = -1
    var airDisplay_mode: Int = -1
    var IGN_ON: Int = -1
    var airTempInCar: Int = -1
    var airTempOutCar: Int = -1
    var ions_state: Int = -1
    var ions_switch: Int = -1
    var ac_power_switch: Int = -1
    var airACHeatStatus: Int = -1
    var airTempLevel: Int = -1
    var airCompressorErrorStatus: Int = -1
    var airCompressorLimitStatus: Int = -1
    var airCompressorWorkStatus: Int = -1
    var airPTCErrorStatus: Int = -1
    var airPTCLimitStatus: Int = -1
    var airPTCWorKStatus: Int = -1
    var airEngineStatus: Int = -1
    var airPm2_5: Int = -1
    var acRunningState: Int = -1
    var airAQSStatus: Int = -1
    var airPMStatus: Int = -1
    var airPreSupplyStatus: Int = -1
    var acRapidCooling: Int = -1
    var acOneButtonWarmth: Int = -1
    var acHazeMode: Int = -1
    var acBabyCareMode: Int = -1
    var acAirCleanerMode: Int = -1
    var acRainSnowMode: Int = -1
    var acSmokingMode: Int = -1
    var acStopCarMode: Int = -1
    var displaypop: Int = -1

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(airSWStatus)
        dest.writeInt(airACStatus)
        dest.writeInt(airHighWindStatus)
        dest.writeInt(airLowWindStatus)
        dest.writeInt(airDUALStatus)
        dest.writeInt(airMaxFrontStatus)
        dest.writeInt(airRearLightStatus)
        dest.writeInt(airSupplyStatus)
        dest.writeInt(airDisplaySW)
        dest.writeInt(airWindSpeed)
        dest.writeFloat(airLeftTemperature)
        dest.writeFloat(airRightTemperature)
        dest.writeFloat(airRearTemperature)
        dest.writeInt(airCirculationMode)
        dest.writeInt(airLeftSeatHeatingLevel)
        dest.writeInt(airRearCtlLockSW)
        dest.writeInt(airACMaxSW)
        dest.writeInt(airRightSeatHeatingLevel)
        dest.writeInt(airRearWindowHeatingStatus)
        dest.writeInt(airECO)
        dest.writeInt(airFrontWindowDefogger)
        dest.writeInt(airMode)
        dest.writeInt(airCirculationStatus)
        dest.writeInt(airRearWindSpeed)
        dest.writeInt(airRearDownSupplyStatus)
        dest.writeInt(airRearParallelSupplyStatus)
        dest.writeInt(airRearUpSupplyStatus)
        dest.writeInt(airRearWindowDefogger)
        dest.writeInt(airLeftMirrorDefogger)
        dest.writeInt(airRightMirrorDefogger)
        dest.writeInt(airSyncStatus)
        dest.writeInt(airDisplay_mode)
        dest.writeInt(IGN_ON)
        dest.writeInt(airTempInCar)
        dest.writeInt(airTempOutCar)
        dest.writeInt(ions_state)
        dest.writeInt(ions_switch)
        dest.writeInt(ac_power_switch)
        dest.writeInt(airACHeatStatus)
        dest.writeInt(airTempLevel)
        dest.writeInt(airCompressorErrorStatus)
        dest.writeInt(airCompressorLimitStatus)
        dest.writeInt(airCompressorWorkStatus)
        dest.writeInt(airPTCErrorStatus)
        dest.writeInt(airPTCLimitStatus)
        dest.writeInt(airPTCWorKStatus)
        dest.writeInt(airEngineStatus)
        dest.writeInt(airPm2_5)
        dest.writeInt(acRunningState)
        dest.writeInt(airAQSStatus)
        dest.writeInt(airPMStatus)
        dest.writeInt(airPreSupplyStatus)
        dest.writeInt(acRapidCooling)
        dest.writeInt(acOneButtonWarmth)
        dest.writeInt(acHazeMode)
        dest.writeInt(acBabyCareMode)
        dest.writeInt(acAirCleanerMode)
        dest.writeInt(acRainSnowMode)
        dest.writeInt(acSmokingMode)
        dest.writeInt(acStopCarMode)
        dest.writeInt(displaypop)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<AirCondition> = object : Parcelable.Creator<AirCondition> {
            override fun createFromParcel(source: Parcel): AirCondition {
                return AirCondition().apply {
                    airSWStatus = source.readInt()
                    airACStatus = source.readInt()
                    airHighWindStatus = source.readInt()
                    airLowWindStatus = source.readInt()
                    airDUALStatus = source.readInt()
                    airMaxFrontStatus = source.readInt()
                    airRearLightStatus = source.readInt()
                    airSupplyStatus = source.readInt()
                    airDisplaySW = source.readInt()
                    airWindSpeed = source.readInt()
                    airLeftTemperature = source.readFloat()
                    airRightTemperature = source.readFloat()
                    airRearTemperature = source.readFloat()
                    airCirculationMode = source.readInt()
                    airLeftSeatHeatingLevel = source.readInt()
                    airRearCtlLockSW = source.readInt()
                    airACMaxSW = source.readInt()
                    airRightSeatHeatingLevel = source.readInt()
                    airRearWindowHeatingStatus = source.readInt()
                    airECO = source.readInt()
                    airFrontWindowDefogger = source.readInt()
                    airMode = source.readInt()
                    airCirculationStatus = source.readInt()
                    airRearWindSpeed = source.readInt()
                    airRearDownSupplyStatus = source.readInt()
                    airRearParallelSupplyStatus = source.readInt()
                    airRearUpSupplyStatus = source.readInt()
                    airRearWindowDefogger = source.readInt()
                    airLeftMirrorDefogger = source.readInt()
                    airRightMirrorDefogger = source.readInt()
                    airSyncStatus = source.readInt()
                    airDisplay_mode = source.readInt()
                    IGN_ON = source.readInt()
                    airTempInCar = source.readInt()
                    airTempOutCar = source.readInt()
                    ions_state = source.readInt()
                    ions_switch = source.readInt()
                    ac_power_switch = source.readInt()
                    airACHeatStatus = source.readInt()
                    airTempLevel = source.readInt()
                    airCompressorErrorStatus = source.readInt()
                    airCompressorLimitStatus = source.readInt()
                    airCompressorWorkStatus = source.readInt()
                    airPTCErrorStatus = source.readInt()
                    airPTCLimitStatus = source.readInt()
                    airPTCWorKStatus = source.readInt()
                    airEngineStatus = source.readInt()
                    airPm2_5 = source.readInt()
                    acRunningState = source.readInt()
                    airAQSStatus = source.readInt()
                    airPMStatus = source.readInt()
                    airPreSupplyStatus = source.readInt()
                    acRapidCooling = source.readInt()
                    acOneButtonWarmth = source.readInt()
                    acHazeMode = source.readInt()
                    acBabyCareMode = source.readInt()
                    acAirCleanerMode = source.readInt()
                    acRainSnowMode = source.readInt()
                    acSmokingMode = source.readInt()
                    acStopCarMode = source.readInt()
                    displaypop = source.readInt()
                }
            }

            override fun newArray(size: Int): Array<AirCondition?> = arrayOfNulls(size)
        }
    }
}
