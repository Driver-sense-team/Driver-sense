package kkam.app

import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import kkam.engine.model.Gear
import kkam.engine.model.HeadlightState
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kkam.engine.model.WiperMode
import kotlin.math.roundToInt
import ai.pleos.playground.navi.constants.TurnType as NaviTurnType

// SDK 값을 엔진 값으로 바꾼다. 규칙의 기준은 docs/spec.md 2.1의 변환표다.

private const val MPS_TO_KPH = 3.6

/** PERF_VEHICLE_SPEED는 m/s로 온다. */
fun mpsToKph(mps: Float): Double = mps * MPS_TO_KPH

/** GEAR_UNKNOWN이면 null. 수집기는 직전 값을 유지한다. */
fun VehicleGear.toEngineGear(): Gear? = when (this) {
    VehicleGear.GEAR_PARK -> Gear.P
    VehicleGear.GEAR_REVERSE -> Gear.R
    VehicleGear.GEAR_NEUTRAL -> Gear.N
    VehicleGear.GEAR_UNKNOWN -> null
    else -> Gear.D // GEAR_DRIVE, GEAR_1 ~ GEAR_9
}

fun TurnSignalValue.toEngineTurnSignal(): TurnSignal = when (this) {
    TurnSignalValue.LEFT -> TurnSignal.LEFT
    TurnSignalValue.RIGHT -> TurnSignal.RIGHT
    else -> TurnSignal.OFF
}

/** 목록의 첫 항목이 다음 안내다. 경로가 없으면 목록이 비어 있다. */
fun List<TBTInfo>.toEngineTbt(): Tbt? = firstOrNull()?.let {
    Tbt(it.type.toEngineTurnType(), it.distance.roundToInt())
}

fun NaviTurnType.toEngineTurnType(): TurnType = when (this) {
    NaviTurnType.TURN_LEFT, NaviTurnType.TURN_SHARP_L -> TurnType.LEFT
    NaviTurnType.TURN_RIGHT, NaviTurnType.TURN_SHARP_R -> TurnType.RIGHT
    NaviTurnType.TURN_UTURN -> TurnType.UTURN
    NaviTurnType.TURN_STRAIGHT -> TurnType.STRAIGHT
    NaviTurnType.TURN_ENTER_IC_L, NaviTurnType.TURN_EXIT_IC_L -> TurnType.RAMP_LEFT
    NaviTurnType.TURN_ENTER_IC_R, NaviTurnType.TURN_EXIT_IC_R -> TurnType.RAMP_RIGHT
    NaviTurnType.TURN_TUNNEL -> TurnType.TUNNEL
    // 회전교차로 진입. 숫자는 나갈 출구의 시계 방향이고 엔진은 쓰지 않는다 (spec 14장)
    NaviTurnType.TURN_ENTER_ROUNDABOUT_02, NaviTurnType.TURN_ENTER_ROUNDABOUT_03, NaviTurnType.TURN_ENTER_ROUNDABOUT_04,
    NaviTurnType.TURN_ENTER_ROUNDABOUT_06, NaviTurnType.TURN_ENTER_ROUNDABOUT_08, NaviTurnType.TURN_ENTER_ROUNDABOUT_09,
    NaviTurnType.TURN_ENTER_ROUNDABOUT_10, NaviTurnType.TURN_ENTER_ROUNDABOUT_12,
    -> TurnType.ROUNDABOUT_ENTER
    NaviTurnType.TURN_EXIT_ROUNDABOUT -> TurnType.ROUNDABOUT_EXIT
    else -> TurnType.OTHER // TURN_KEEP_*, 방향 없는 IC(TURN_*_IC_C) 등
}

/**
 * 하향등 상태와 스위치를 엔진 값으로 (spec 2.1). 상태가 ON이면 ON, 아니고 스위치가 AUTOMATIC이면 AUTO,
 * 그 밖(상태 OFF, DAYTIME_RUNNING)은 OFF. 상태를 아직 못 읽었으면 null.
 */
fun headlightOf(state: LightState?, switch: LightSwitch?): HeadlightState? = when {
    state == null -> null
    state == LightState.ON -> HeadlightState.ON
    switch == LightSwitch.AUTOMATIC -> HeadlightState.AUTO
    else -> HeadlightState.OFF
}

/**
 * 와이퍼 상태와 스위치를 엔진 값으로 (spec 2.1). 스위치가 AUTO면 AUTO, 아니면 상태 ON은 ON, OFF는 OFF.
 * 상태가 OTHER·SERVICE이거나 아직 못 읽었으면 null이다. 수집기는 이때 직전 값을 유지한다.
 */
fun wiperOf(state: WiperState?, switch: WiperSwitch?): WiperMode? = when {
    switch == WiperSwitch.AUTO -> WiperMode.AUTO
    state == WiperState.ON -> WiperMode.ON
    state == WiperState.OFF -> WiperMode.OFF
    else -> null
}
