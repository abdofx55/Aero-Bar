package com.aero.bar.data.repository

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.util.Log
import com.aero.bar.domain.model.HvacTelemetryState
import com.aero.bar.domain.repository.VehicleHvacRepository
import com.qinggan.canbus.AirCondition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardware implementation of [VehicleHvacRepository] for Dongfeng Shine (Aeolus D53).
 *
 * Connects directly via Android Binder (`android.os.ServiceManager` & `bindService`) to:
 * 1. `qg.canbus` (`com.qinggan.canbus.ICanBusService`) — Real-time CAN Bus HVAC telemetry & commands.
 * 2. `vehicle.service` (`com.qinggan.vehicle.IVehicleService`) — Full Climate & Seats window state.
 * 3. `qg.power_manager` (`com.qinggan.system.IPowerManagerService`) — Screen Standby / Wake.
 * 4. `com.qinggan.systempolicy.SystemPolicyService` (`com.qinggan.system.ISystemPolicy`) — 360° AVM & Reverse gear.
 *
 * Also bridges `com.dongfeng.intent.action.HVAC_CONTROL` broadcasts from `Aero_Voice`,
 * and automatically provides reactive simulated state transitions when running on an emulator.
 */
@Singleton
class QingganVehicleHvacRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : VehicleHvacRepository {

    companion object {
        private const val TAG = "QingganHvacRepo"

        // Service Manager Names & Actions
        private const val SERVICE_CANBUS = "qg.canbus"
        private const val SERVICE_VEHICLE = "vehicle.service"
        private const val SERVICE_POWER = "qg.power_manager"
        private const val ACTION_REVERSE_SERVICE = "com.qinggan.systempolicy.SystemPolicyService"
        private const val PKG_REVERSE_SERVICE = "com.qinggan.systempolicy"

        // Binder Interface Descriptors
        private const val DESC_CANBUS = "com.qinggan.canbus.ICanBusService"
        private const val DESC_CANBUS_CB = "com.qinggan.canbus.ICanBusServiceCallback"
        private const val DESC_VEHICLE = "com.qinggan.vehicle.IVehicleService"
        private const val DESC_VEHICLE_CB = "com.qinggan.vehicle.IVehicleServiceCallBack"
        private const val DESC_POWER = "com.qinggan.system.IPowerManagerService"
        private const val DESC_SYSTEM_POLICY = "com.qinggan.system.ISystemPolicy"
        private const val DESC_REVERSE_CB = "com.qinggan.system.IReverseCallback"

        // ICanBusService Transaction Codes (Verified from SystemUI classes.dex)
        private const val TX_CANBUS_ADD_CALLBACK = 27
        private const val TX_CANBUS_GET_AIR_CONDITION = 29
        private const val TX_CANBUS_GET_ACC_STATUS = 63
        private const val TX_CANBUS_OPEN_AC_SWITCH = 88
        private const val TX_CANBUS_CLOSE_AC_SWITCH = 89
        private const val TX_CANBUS_OPEN_AUTO_MODE = 90
        private const val TX_CANBUS_CLOSE_AUTO_MODE = 91
        private const val TX_CANBUS_OPEN_FRONT_DEFROST = 92
        private const val TX_CANBUS_CLOSE_FRONT_DEFROST = 93
        private const val TX_CANBUS_TEMP_UP = 94
        private const val TX_CANBUS_TEMP_DOWN = 95
        private const val TX_CANBUS_WIND_UP = 96
        private const val TX_CANBUS_WIND_DOWN = 97
        private const val TX_CANBUS_SWITCH_CIRCULATION = 98

        // ICanBusServiceCallback Transaction Codes
        private const val TX_CB_ON_AIR_CONDITION_CHANGED = 4
        private const val TX_CB_ON_ACC_STATE_CHANGED = 39

        // IVehicleService Transaction Codes
        private const val TX_VEHICLE_REGISTER_CB = 1
        private const val TX_VEHICLE_CLOSE_VEHICLE = 20

        // IVehicleServiceCallBack Transaction Codes
        private const val TX_VCB_UPDATE_VIEW_STATE = 11

        // IPowerManagerService Transaction Codes
        private const val TX_POWER_GET_AVN_STATUS = 3
        private const val TX_POWER_EXIT_SCREEN_OFF = 6
        private const val TX_POWER_IS_SCREEN_OFF = 14
        private const val TX_POWER_ENTER_STANDBY = 19

        // ISystemPolicy (ReverseManager) Transaction Codes
        private const val TX_POLICY_REGISTER_REVERSE_CB = 12
        private const val TX_POLICY_SHOW_AVM = 20
        private const val TX_POLICY_GET_AVM_STATE = 29

        // IReverseCallback Transaction Codes
        private const val TX_RCB_ON_REVERSE_CHANGE = 2
        private const val TX_RCB_ON_AVM_STATE_CHANGE = 4

        // Aero_Voice Broadcast Action
        private const val ACTION_AERO_VOICE_HVAC = "com.dongfeng.intent.action.HVAC_CONTROL"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _telemetryState = MutableStateFlow(HvacTelemetryState())
    override val telemetryState: StateFlow<HvacTelemetryState> = _telemetryState.asStateFlow()

    @Volatile private var canBusBinder: IBinder? = null
    @Volatile private var vehicleBinder: IBinder? = null
    @Volatile private var powerBinder: IBinder? = null
    @Volatile private var systemPolicyBinder: IBinder? = null

    private val canBusCallbackBinder = object : Binder(), IInterface {
        init {
            attachInterface(this, DESC_CANBUS_CB)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TX_CB_ON_AIR_CONDITION_CHANGED -> {
                    data.enforceInterface(DESC_CANBUS_CB)
                    val air = if (data.readInt() != 0) AirCondition.CREATOR.createFromParcel(data) else null
                    if (air != null) {
                        applyAirConditionFromCanBus(air)
                    }
                    true
                }
                TX_CB_ON_ACC_STATE_CHANGED -> {
                    data.enforceInterface(DESC_CANBUS_CB)
                    val accState = data.readInt()
                    val operational = accState == 2 || accState == 3
                    _telemetryState.update { it.copy(isAccOperational = operational) }
                    true
                }
                IBinder.INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESC_CANBUS_CB)
                    true
                }
                else -> true // Ignore other CAN bus telemetry callbacks safely
            }
        }
    }

    private val vehicleCallbackBinder = object : Binder(), IInterface {
        init {
            attachInterface(this, DESC_VEHICLE_CB)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TX_VCB_UPDATE_VIEW_STATE -> {
                    data.enforceInterface(DESC_VEHICLE_CB)
                    val isFront = data.readInt() != 0
                    _telemetryState.update { it.copy(isClimatePanelOpen = isFront) }
                    true
                }
                IBinder.INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESC_VEHICLE_CB)
                    true
                }
                else -> true
            }
        }
    }

    private val reverseCallbackBinder = object : Binder(), IInterface {
        init {
            attachInterface(this, DESC_REVERSE_CB)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TX_RCB_ON_REVERSE_CHANGE -> {
                    data.enforceInterface(DESC_REVERSE_CB)
                    val state = data.readInt()
                    _telemetryState.update { it.copy(isReverseGearActive = state == 1) }
                    reply?.writeNoException()
                    true
                }
                TX_RCB_ON_AVM_STATE_CHANGE -> {
                    data.enforceInterface(DESC_REVERSE_CB)
                    val state = data.readInt()
                    _telemetryState.update { it.copy(is360CameraActive = state == 1) }
                    reply?.writeNoException()
                    true
                }
                IBinder.INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESC_REVERSE_CB)
                    true
                }
                else -> {
                    reply?.writeNoException()
                    true
                }
            }
        }
    }

    private val reverseServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            systemPolicyBinder = service
            if (service != null) {
                registerBinderCallback(service, DESC_SYSTEM_POLICY, TX_POLICY_REGISTER_REVERSE_CB, reverseCallbackBinder)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            systemPolicyBinder = null
        }
    }

    init {
        connectHardwareServices()
        registerAeroVoiceBridge()
    }

    private fun connectHardwareServices() {
        scope.launch {
            val cb = getSystemBinder(SERVICE_CANBUS)
            val vb = getSystemBinder(SERVICE_VEHICLE)
            val pb = getSystemBinder(SERVICE_POWER)

            canBusBinder = cb
            vehicleBinder = vb
            powerBinder = pb

            if (cb != null) {
                Log.i(TAG, "Connected to hardware CAN Bus service ($SERVICE_CANBUS)")
                _telemetryState.update { it.copy(isHardwareConnected = true) }
                registerBinderCallback(cb, DESC_CANBUS, TX_CANBUS_ADD_CALLBACK, canBusCallbackBinder)
                queryInitialCanBusState(cb)
                cb.linkToDeath({
                    Log.w(TAG, "CAN Bus service died; scheduling reconnect...")
                    canBusBinder = null
                    _telemetryState.update { it.copy(isHardwareConnected = false) }
                    scope.launch {
                        delay(1000L)
                        connectHardwareServices()
                    }
                }, 0)
            } else {
                Log.i(TAG, "Qinggan CAN Bus not present (Emulator mode) — using interactive simulation.")
            }

            if (vb != null) {
                registerBinderCallback(vb, DESC_VEHICLE, TX_VEHICLE_REGISTER_CB, vehicleCallbackBinder)
            }

            // Bind Reverse / 360 AVM SystemPolicyService if present on device
            try {
                val intent = Intent(ACTION_REVERSE_SERVICE).apply {
                    setPackage(PKG_REVERSE_SERVICE)
                }
                context.bindService(intent, reverseServiceConnection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                Log.v(TAG, "SystemPolicyService bind skipped on emulator: ${e.message}")
            }
        }
    }

    private fun registerAeroVoiceBridge() {
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != ACTION_AERO_VOICE_HVAC) return
                    val cmd = intent.getStringExtra("command") ?: return
                    val value = intent.getIntExtra("value", 0)
                    Log.d(TAG, "Received Aero_Voice HVAC command: $cmd = $value")
                    when (cmd) {
                        "AC_POWER" -> if ((value == 1) != _telemetryState.value.isAcOn) toggleAcPower()
                        "AC_AUTO" -> if (!_telemetryState.value.isAutoMode) toggleAutoMode()
                        "TEMP_STEP" -> stepTemperature(value)
                        "FAN_STEP" -> stepFanSpeed(value)
                        "DEFROST_FRONT" -> if ((value == 1) != _telemetryState.value.isFrontDefrostOn) toggleFrontDefrost()
                        "RECIRCULATION" -> if ((value == 1) != _telemetryState.value.isInternalRecirculation) toggleAirRecirculation()
                    }
                }
            }
            val filter = IntentFilter(ACTION_AERO_VOICE_HVAC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Aero_Voice HVAC receiver: ${e.message}")
        }
    }

    private fun getSystemBinder(serviceName: String): IBinder? {
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getMethod("getService", String::class.java)
            getService.invoke(null, serviceName) as? IBinder
        } catch (e: Exception) {
            null
        }
    }

    private fun registerBinderCallback(service: IBinder, descriptor: String, txCode: Int, callback: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(descriptor)
            data.writeStrongBinder(callback)
            service.transact(txCode, data, reply, 0)
            reply.readException()
        } catch (e: Exception) {
            Log.w(TAG, "registerBinderCallback($descriptor) error: ${e.message}")
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun queryInitialCanBusState(cb: IBinder) {
        // 1. Query ACC Status
        val accStatus = transactIntResult(cb, DESC_CANBUS, TX_CANBUS_GET_ACC_STATUS)
        if (accStatus >= 0) {
            _telemetryState.update { it.copy(isAccOperational = accStatus == 2 || accStatus == 3) }
        }

        // 2. Query Initial AirCondition Parcelable
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESC_CANBUS)
            if (cb.transact(TX_CANBUS_GET_AIR_CONDITION, data, reply, 0)) {
                reply.readException()
                if (reply.readInt() != 0) {
                    val air = AirCondition.CREATOR.createFromParcel(reply)
                    applyAirConditionFromCanBus(air)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryInitialCanBusState error: ${e.message}")
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun applyAirConditionFromCanBus(air: AirCondition) {
        val acOn = air.airSWStatus == 1
        val autoOn = air.airMode == 1
        val tempC = if (air.airLeftTemperature in 16f..32f) air.airLeftTemperature.toInt() else _telemetryState.value.temperatureC
        val wind = if (air.airWindSpeed in 1..8) air.airWindSpeed else _telemetryState.value.fanSpeed
        val defrost = air.airFrontWindowDefogger == 1
        val internalLoop = air.airCirculationMode == 1
        val supplyMode = if (air.airSupplyStatus >= 0) air.airSupplyStatus else _telemetryState.value.airSupplyMode

        _telemetryState.update {
            it.copy(
                isAcOn = acOn,
                isAutoMode = autoOn && acOn,
                temperatureC = tempC,
                fanSpeed = wind,
                airSupplyMode = supplyMode,
                isFrontDefrostOn = defrost && acOn,
                isInternalRecirculation = internalLoop
            )
        }
    }

    private fun transactBooleanCmd(binder: IBinder?, descriptor: String, txCode: Int): Boolean {
        val b = binder ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(descriptor)
            val ok = b.transact(txCode, data, reply, 0)
            reply.readException()
            ok
        } catch (e: Exception) {
            Log.w(TAG, "transactBooleanCmd($descriptor, $txCode) failed: ${e.message}")
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactIntArgResult(binder: IBinder?, descriptor: String, txCode: Int, arg: Int): Int {
        val b = binder ?: return -1
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(descriptor)
            data.writeInt(arg)
            b.transact(txCode, data, reply, 0)
            reply.readException()
            reply.readInt()
        } catch (e: Exception) {
            Log.w(TAG, "transactIntArgResult($descriptor, $txCode) failed: ${e.message}")
            -1
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactIntResult(binder: IBinder?, descriptor: String, txCode: Int): Int {
        val b = binder ?: return -1
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(descriptor)
            b.transact(txCode, data, reply, 0)
            reply.readException()
            reply.readInt()
        } catch (e: Exception) {
            -1
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    override fun wakeScreenIfOff(): Boolean {
        val pb = powerBinder
        if (pb != null) {
            val isOff = transactIntResult(pb, DESC_POWER, TX_POWER_IS_SCREEN_OFF) == 1
            if (isOff) {
                transactBooleanCmd(pb, DESC_POWER, TX_POWER_EXIT_SCREEN_OFF)
                _telemetryState.update { it.copy(isScreenStandby = false) }
                return true
            }
        } else if (_telemetryState.value.isScreenStandby) {
            _telemetryState.update { it.copy(isScreenStandby = false) }
            return true
        }
        return false
    }

    override fun toggleAcPower() {
        if (wakeScreenIfOff()) return
        val current = _telemetryState.value
        val nextAc = !current.isAcOn
        val cb = canBusBinder
        if (cb != null) {
            val tx = if (nextAc) TX_CANBUS_OPEN_AC_SWITCH else TX_CANBUS_CLOSE_AC_SWITCH
            transactBooleanCmd(cb, DESC_CANBUS, tx)
        }
        _telemetryState.update {
            it.copy(
                isAcOn = nextAc,
                isAutoMode = if (!nextAc) false else it.isAutoMode,
                isFrontDefrostOn = if (!nextAc) false else it.isFrontDefrostOn
            )
        }
    }

    override fun toggleAutoMode() {
        if (wakeScreenIfOff()) return
        val current = _telemetryState.value
        val nextAuto = !current.isAutoMode
        val cb = canBusBinder
        if (cb != null) {
            val tx = if (nextAuto) TX_CANBUS_OPEN_AUTO_MODE else TX_CANBUS_CLOSE_AUTO_MODE
            transactBooleanCmd(cb, DESC_CANBUS, tx)
        }
        _telemetryState.update {
            it.copy(
                isAcOn = if (nextAuto) true else it.isAcOn,
                isAutoMode = nextAuto
            )
        }
    }

    override fun stepTemperature(deltaSteps: Int) {
        if (wakeScreenIfOff() || deltaSteps == 0) return
        val cb = canBusBinder
        if (cb != null) {
            val tx = if (deltaSteps > 0) TX_CANBUS_TEMP_UP else TX_CANBUS_TEMP_DOWN
            repeat(kotlin.math.abs(deltaSteps).coerceAtMost(5)) {
                transactBooleanCmd(cb, DESC_CANBUS, tx)
            }
        }
        _telemetryState.update { current ->
            val nextTemp = (current.temperatureC + deltaSteps).coerceIn(
                HvacTelemetryState.MIN_TEMP_C,
                HvacTelemetryState.MAX_TEMP_C
            )
            current.copy(
                isAcOn = true,
                temperatureC = nextTemp
            )
        }
    }

    override fun stepFanSpeed(deltaSteps: Int) {
        if (wakeScreenIfOff() || deltaSteps == 0) return
        val cb = canBusBinder
        if (cb != null) {
            val tx = if (deltaSteps > 0) TX_CANBUS_WIND_UP else TX_CANBUS_WIND_DOWN
            repeat(kotlin.math.abs(deltaSteps).coerceAtMost(4)) {
                transactBooleanCmd(cb, DESC_CANBUS, tx)
            }
        }
        _telemetryState.update { current ->
            val nextFan = (current.fanSpeed + deltaSteps).coerceIn(
                HvacTelemetryState.MIN_FAN_SPEED,
                HvacTelemetryState.MAX_FAN_SPEED
            )
            current.copy(
                isAcOn = true,
                isAutoMode = false, // Manual fan adjustment exits AUTO mode per vehicle logic
                fanSpeed = nextFan
            )
        }
    }

    override fun toggleClimateAndSeatsApp() {
        if (wakeScreenIfOff()) return
        val currentlyOpen = _telemetryState.value.isClimatePanelOpen
        if (currentlyOpen) {
            val vb = vehicleBinder
            if (vb != null) {
                transactBooleanCmd(vb, DESC_VEHICLE, TX_VEHICLE_CLOSE_VEHICLE)
            }
            _telemetryState.update { it.copy(isClimatePanelOpen = false) }
            com.aero.bar.service.AeroAccessibilityService.performBack()
        } else {
            if (_telemetryState.value.is360CameraActive) {
                val sp = systemPolicyBinder ?: getSystemBinder("qg.system_policy")
                sp?.let { transactIntArgResult(it, DESC_SYSTEM_POLICY, TX_POLICY_SHOW_AVM, 0) }
            }
            // Launch original Dongfeng Climate, Seat Heating & Airflow Distribution Activity
            try {
                val intent = Intent().apply {
                    setClassName("com.qinggan.app.vehicle", "com.qinggan.app.vehicle.MainActivity")
                    putExtra("TAB_ID", 2) // Supports Seat Heating & Airflow tabs
                    putExtra("MTFS_AC", -1)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                context.startActivity(intent)
                _telemetryState.update {
                    it.copy(
                        isClimatePanelOpen = true,
                        is360CameraActive = false
                    )
                }
            } catch (e: Exception) {
                Log.i(TAG, "com.qinggan.app.vehicle not installed on emulator; toggling state for UI preview")
                _telemetryState.update {
                    it.copy(
                        isClimatePanelOpen = !it.isClimatePanelOpen,
                        is360CameraActive = false
                    )
                }
            }
        }
    }

    override fun toggleFrontDefrost() {
        if (wakeScreenIfOff()) return
        val nextDefrost = !_telemetryState.value.isFrontDefrostOn
        val cb = canBusBinder
        if (cb != null) {
            val tx = if (nextDefrost) TX_CANBUS_OPEN_FRONT_DEFROST else TX_CANBUS_CLOSE_FRONT_DEFROST
            transactBooleanCmd(cb, DESC_CANBUS, tx)
        }
        _telemetryState.update {
            it.copy(
                isAcOn = if (nextDefrost) true else it.isAcOn,
                isFrontDefrostOn = nextDefrost
            )
        }
    }

    override fun toggleAirRecirculation() {
        if (wakeScreenIfOff()) return
        val cb = canBusBinder
        if (cb != null) {
            transactBooleanCmd(cb, DESC_CANBUS, TX_CANBUS_SWITCH_CIRCULATION)
        }
        _telemetryState.update {
            it.copy(isInternalRecirculation = !it.isInternalRecirculation)
        }
    }

    override fun toggle360Camera() {
        if (wakeScreenIfOff()) return
        val nextAvm = !_telemetryState.value.is360CameraActive
        if (nextAvm && _telemetryState.value.isClimatePanelOpen) {
            vehicleBinder?.let { transactBooleanCmd(it, DESC_VEHICLE, TX_VEHICLE_CLOSE_VEHICLE) }
        }
        val sp = systemPolicyBinder ?: getSystemBinder("qg.system_policy")
        if (sp != null) {
            val req = if (nextAvm) 1 else 0
            transactIntArgResult(sp, DESC_SYSTEM_POLICY, TX_POLICY_SHOW_AVM, req)
            _telemetryState.update {
                it.copy(
                    is360CameraActive = nextAvm,
                    isClimatePanelOpen = if (nextAvm) false else it.isClimatePanelOpen
                )
            }
            if (!nextAvm) {
                com.aero.bar.service.AeroAccessibilityService.performBack()
            }
            return
        }

        // Fallback: Try launching car's 360 Camera / DVR package if installed
        val cameraPackages = listOf(
            "pateo.Application.Camera" to "pateo.Application.Camera.CameraActivity",
            "com.miren.dvr" to "",
            "pateo.Application.CarRecord" to "pateo.Application.CarRecord.CarRecordActivity"
        )
        for ((pkg, act) in cameraPackages) {
            try {
                val intent = if (act.isNotEmpty()) {
                    Intent().setClassName(pkg, act)
                } else {
                    context.packageManager.getLaunchIntentForPackage(pkg)
                }
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    _telemetryState.update {
                        it.copy(
                            is360CameraActive = true,
                            isClimatePanelOpen = false
                        )
                    }
                    return
                }
            } catch (_: Exception) {}
        }

        // On emulator: toggle active indicator state
        _telemetryState.update {
            it.copy(
                is360CameraActive = nextAvm,
                isClimatePanelOpen = if (nextAvm) false else it.isClimatePanelOpen
            )
        }
    }

    override fun dismissVehiclePanels() {
        val current = _telemetryState.value
        if (current.isClimatePanelOpen) {
            vehicleBinder?.let { transactBooleanCmd(it, DESC_VEHICLE, TX_VEHICLE_CLOSE_VEHICLE) }
        }
        if (current.is360CameraActive) {
            val sp = systemPolicyBinder ?: getSystemBinder("qg.system_policy")
            sp?.let { transactIntArgResult(it, DESC_SYSTEM_POLICY, TX_POLICY_SHOW_AVM, 0) }
        }
        if (current.isClimatePanelOpen || current.is360CameraActive) {
            _telemetryState.update {
                it.copy(
                    isClimatePanelOpen = false,
                    is360CameraActive = false
                )
            }
        }
    }

    override fun enterScreenStandby() {
        if (wakeScreenIfOff()) return
        val pb = powerBinder
        if (pb != null) {
            transactBooleanCmd(pb, DESC_POWER, TX_POWER_ENTER_STANDBY)
        } else {
            // Try launching Dongfeng Standby Clock activity if present
            try {
                val intent = Intent().apply {
                    setClassName("com.qinggan.standby", "com.qinggan.standby.MainActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
        _telemetryState.update { it.copy(isScreenStandby = true) }
    }
}
