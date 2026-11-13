package kkam.app

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kkam.engine.model.Gear
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.WiperMode

/**
 * 차량 신호를 모아 1초에 한 번 [SignalFrame]을 만든다.
 *
 * SDK 콜백은 다른 스레드에서 올 수 있어서 모두 메인 스레드로 넘긴다. Java의 synchronized 대신
 * 값을 만지는 스레드를 하나로 고정한 것이다. 값이 잠시 없으면 직전 값을 유지하고(spec 2.1),
 * 필수 값이 한 번도 안 들어왔으면 프레임을 만들지 않는다.
 */
class SignalCollector(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var onFrame: ((SignalFrame) -> Unit)? = null

    private var speedKph: Double? = null
    private var gear: Gear? = null
    private var steeringDeg: Double? = null
    private var turnSignal: TurnSignal? = null
    private var brake: Boolean? = null
    private var position: LocationData? = null
    private var tbt: Tbt? = null
    /** 하향등 상태와 스위치. 없어도 프레임은 만든다(headlight = null, spec 2.1). */
    private var lowBeamState: LightState? = null
    private var lowBeamSwitch: LightSwitch? = null
    /** 앞 유리 와이퍼 상태와 스위치. 마지막으로 알아낸 엔진 값을 따로 둔다 (OTHER·SERVICE면 직전 값 유지, spec 2.1). */
    private var wiperState: WiperState? = null
    private var wiperSwitch: WiperSwitch? = null
    private var lastWiper: WiperMode? = null
    /** 앞유리 성에 제거와 내기 순환. R7에서만 쓴다. 없어도 프레임은 만든다. */
    private var defroster: Boolean? = null
    private var recirc: Boolean? = null

    private var vehicle: Vehicle? = null
    private var navi: NaviHelper? = null
    private var fused: FusedLocation? = null
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null

    private val tick = object : Runnable {
        override fun run() {
            navi?.getTBTInfo()
            readLowBeamSwitch()
            readWiperSwitch()
            snapshot()?.let { frame -> onFrame?.invoke(frame) }
            main.postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    fun start(onFrame: (SignalFrame) -> Unit) {
        this.onFrame = onFrame
        startSpeed()
        startNavi()
        startVehicleAndLocation()
        main.post(tick)
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        onFrame = null
        propertyManager?.unregisterCallback(speedListener)
        car?.disconnect()
        navi?.let {
            it.removeListener(naviListener)
            it.release()
        }
        fused?.let {
            it.unregisterFusedLocationCallback(locationListener)
            it.release()
        }
        vehicle?.let {
            it.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
            it.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener)
            it.getGear().unregisterGearSelection(gearListener)
            it.getBrake().unregisterBrakePedalStatus(brakeListener)
            runCatching { it.getLight().unregisterLowBeamLightState(lowBeamListener) }
            runCatching { it.getWiper().unregisterWiperState(FRONT, wiperListener) }
            runCatching { it.getHVAC().unregisterHvacDefrosterState(FRONT, defrosterListener) }
            runCatching { it.getHVAC().unregisterHvacRecirculationState(CABIN, recircListener) }
            it.release()
        }
        lowBeamState = null
        lowBeamSwitch = null
        wiperState = null
        wiperSwitch = null
        lastWiper = null
        defroster = null
        recirc = null
        propertyManager = null
        car = null
        navi = null
        fused = null
        vehicle = null
    }

    private fun snapshot(): SignalFrame? {
        val frame = SignalFrame(
            t = System.currentTimeMillis(),
            speedKph = speedKph ?: return null,
            gear = gear ?: return null,
            steeringDeg = steeringDeg,
            turnSignal = turnSignal ?: return null,
            brake = brake ?: return null,
            lat = position?.latitude ?: return null,
            lon = position?.longitude ?: return null,
            headingDeg = position?.heading?.toDouble() ?: return null,
            tbt = tbt,
            light = null, // 신호 정보는 서버가 붙으면 채운다 (R3)
            headlight = headlightOf(lowBeamState, lowBeamSwitch),
            wiper = (wiperOf(wiperState, wiperSwitch) ?: lastWiper).also { lastWiper = it },
            rain = null, // 비 정보는 서버 날씨가 붙으면 채운다 (R6·R7)
            defroster = defroster,
            recirc = recirc,
            fogReported = null, // 음성 "앞이 뿌옇다"는 STT가 붙으면 채운다 (R7)
        )
        return frame
    }

    /** 속도는 Vehicle SDK에 없어서 CarPropertyManager로 읽는다. */
    private fun startSpeed() {
        val connected = Car.createCar(appContext) ?: return Log.w(TAG, "car service unavailable").let {}
        car = connected
        val manager = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        propertyManager = manager
        manager.registerCallback(speedListener, VehiclePropertyIds.PERF_VEHICLE_SPEED, SPEED_RATE_HZ)
    }

    private fun startNavi() {
        val helper = NaviHelper(appContext)
        navi = helper
        helper.initialize(
            { main.post { navi?.addListener(naviListener) } },
            { error -> Log.w(TAG, "navi connect failed ${error.message}") },
        )
    }

    /** 두 SDK의 initialize()는 완료 콜백이 없어서 연결될 시간을 두고 등록한다. */
    private fun startVehicleAndLocation() {
        val car = Vehicle(appContext).also { it.initialize() }
        val location = FusedLocation(appContext).also { it.initialize() }
        vehicle = car
        fused = location
        main.postDelayed({ registerVehicle(car); location.registerFusedLocationCallback(locationListener) }, SDK_CONNECT_DELAY_MS)
    }

    private fun registerVehicle(car: Vehicle) {
        val onFailed = { error: Exception -> Log.w(TAG, "vehicle read failed ${error.message}").let {} }
        car.getTurnSignal().getCurrentTurnSignalState({ value -> onMain { turnSignal = value?.toEngineTurnSignal() ?: turnSignal } }, onFailed)
        car.getSteeringWheel().getCurrentSteeringWheelAngle({ value -> onMain { steeringDeg = value?.toDouble() ?: steeringDeg } }, onFailed)
        car.getGear().getCurrentGearSelectionState({ value -> onMain { gear = value?.toEngineGear() ?: gear } }, onFailed)
        car.getBrake().getCurrentBrakePedalStatus({ value -> onMain { brake = value ?: brake } }, onFailed)
        car.getTurnSignal().registerTurnSignalState(turnSignalListener)
        car.getSteeringWheel().registerSteeringWheelAngle(steeringListener)
        car.getGear().registerGearSelection(gearListener)
        car.getBrake().registerBrakePedalStatus(brakeListener)
        // 전조등은 R5에서만 쓴다. 권한이나 SDK 문제로 실패해도 나머지 신호는 계속 모은다.
        runCatching {
            car.getLight().getCurrentLowBeamLightState({ value -> onMain { lowBeamState = value ?: lowBeamState } }, onFailed)
            car.getLight().registerLowBeamLightState(lowBeamListener)
        }.onFailure { logFailure("low beam", it as? Exception ?: Exception(it)) }
        // 성에 제거와 내기 순환은 R7에서만 쓴다. CAR_CLIMATE 권한이나 SDK 문제로 실패해도 나머지 신호는 계속 모은다.
        runCatching {
            val hvac = car.getHVAC()
            hvac.getHvacDefrosterState(FRONT, { value -> onMain { defroster = value ?: defroster } }, onFailed)
            hvac.getHvacRecirculationState(CABIN, { value -> onMain { recirc = value ?: recirc } }, onFailed)
            hvac.registerHvacDefrosterState(FRONT, defrosterListener)
            hvac.registerHvacRecirculationState(CABIN, recircListener)
        }.onFailure { logFailure("hvac", it as? Exception ?: Exception(it)) }
        // 와이퍼는 R6에서만 쓴다. CAR_WINDOWS 권한이나 SDK 문제로 실패해도 나머지 신호는 계속 모은다.
        runCatching {
            car.getWiper().getCurrentWiperState(FRONT, { value -> onMain { wiperState = value ?: wiperState } }, onFailed)
            car.getWiper().registerWiperState(FRONT, wiperListener)
        }.onFailure { logFailure("wiper", it as? Exception ?: Exception(it)) }
    }

    /** 와이퍼 스위치(AUTO 여부)는 변경 알림이 없어서 프레임마다 읽는다. 실패해도 다른 신호는 계속 모은다. */
    private fun readWiperSwitch() {
        val car = vehicle ?: return
        runCatching {
            car.getWiper().getCurrentWiperSwitch(FRONT, { value -> onMain { wiperSwitch = value ?: wiperSwitch } }, { _ -> })
        }
    }

    /** 스위치(AUTOMATIC 여부)는 변경 알림이 없어서 프레임마다 읽는다. */
    private fun readLowBeamSwitch() {
        val car = vehicle ?: return
        runCatching {
            car.getLight().getLowBeamLightSwitch({ value -> onMain { lowBeamSwitch = value ?: lowBeamSwitch } }, { _ -> })
        }
    }

    private fun onMain(block: () -> Unit) {
        main.post(block)
    }

    private val speedListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            (value.value as? Float)?.let { mps -> onMain { speedKph = mpsToKph(mps) } }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "speed read failed")
        }
    }

    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) =
            onMain { turnSignal = value?.toEngineTurnSignal() ?: turnSignal }

        override fun onFailed(e: Exception) = logFailure("turn signal", e)
    }

    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(value: Float?) =
            onMain { steeringDeg = value?.toDouble() ?: steeringDeg }

        override fun onFailed(e: Exception) = logFailure("steering", e)
    }

    private val defrosterListener = object : HvacDefrosterStateListener {
        override fun onHvacDefrosterStateUpdated(value: Boolean?) = onMain { defroster = value ?: defroster }

        override fun onFailed(e: Exception) = logFailure("defroster", e)
    }

    private val recircListener = object : HvacRecirculationStateListener {
        override fun onHvacAutoRecirculationStateUpdated(value: Boolean?) = onMain { recirc = value ?: recirc }

        override fun onFailed(e: Exception) = logFailure("recirc", e)
    }

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(value: WiperState?) = onMain { wiperState = value ?: wiperState }

        override fun onFailed(e: Exception) = logFailure("wiper", e)
    }

    private val lowBeamListener = object : LightStateListener {
        override fun onLightStateUpdated(value: LightState?) = onMain { lowBeamState = value ?: lowBeamState }

        override fun onFailed(e: Exception) = logFailure("low beam", e)
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(value: VehicleGear?) = onMain { gear = value?.toEngineGear() ?: gear }

        override fun onFailed(e: Exception) = logFailure("gear", e)
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(value: Boolean?) = onMain { brake = value ?: brake }

        override fun onFailed(e: Exception) = logFailure("brake", e)
    }

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location?.let { onMain { position = it } }
        }

        override fun onError(exception: Exception) = logFailure("location", exception)
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) = onMain { tbt = tbtInfoList.toEngineTbt() }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error $errorCode")
        }
    }

    private fun logFailure(name: String, error: Exception) {
        Log.w(TAG, "$name failed ${error.message}")
    }

    private companion object {
        const val TAG = "KkamCollector"
        /** 앞 유리. 와이퍼와 성에 제거에 쓴다. */
        val FRONT = WindowArea(WindowValue.FRONT_WINDSHIELD)

        /** 내기 순환을 읽는 좌석 영역. 에뮬레이터는 1열 좌·우와 2열 좌·중·우를 묶은 영역만 지원한다 (spec 2.1). */
        val CABIN = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
        const val FRAME_INTERVAL_MS = 1000L
        const val SDK_CONNECT_DELAY_MS = 2000L
        const val SPEED_RATE_HZ = 1f
    }
}
