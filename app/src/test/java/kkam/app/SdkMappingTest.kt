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
import kkam.engine.model.WiperMode
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import ai.pleos.playground.navi.constants.TurnType as NaviTurnType

class SdkMappingTest {
    @Test
    fun `속도는 초속을 시속으로 바꾼다`() {
        assertEquals(36.0, mpsToKph(10f), 0.001)
    }

    @Test
    fun `기어를 엔진 값으로 바꾼다`() {
        assertEquals(Gear.P, VehicleGear.GEAR_PARK.toEngineGear())
        assertEquals(Gear.R, VehicleGear.GEAR_REVERSE.toEngineGear())
        assertEquals(Gear.N, VehicleGear.GEAR_NEUTRAL.toEngineGear())
        assertEquals(Gear.D, VehicleGear.GEAR_DRIVE.toEngineGear())
        assertEquals(Gear.D, VehicleGear.GEAR_3.toEngineGear())
        assertNull(VehicleGear.GEAR_UNKNOWN.toEngineGear())
    }

    @Test
    fun `방향지시등을 엔진 값으로 바꾼다`() {
        assertEquals(TurnSignal.LEFT, TurnSignalValue.LEFT.toEngineTurnSignal())
        assertEquals(TurnSignal.RIGHT, TurnSignalValue.RIGHT.toEngineTurnSignal())
        assertEquals(TurnSignal.OFF, TurnSignalValue.OFF.toEngineTurnSignal())
    }

    @Test
    fun `TBT는 첫 항목을 쓰고 거리를 반올림한다`() {
        val list = listOf(
            TBTInfo(NaviTurnType.TURN_LEFT, 79.6, "첫 안내"),
            TBTInfo(NaviTurnType.TURN_RIGHT, 390.0, "다음 안내"),
        )

        assertEquals(Tbt(TurnType.LEFT, 80), list.toEngineTbt())
        assertNull(emptyList<TBTInfo>().toEngineTbt())
    }

    @Test
    fun `회전 종류는 spec 변환표를 따른다`() {
        assertEquals(TurnType.LEFT, NaviTurnType.TURN_SHARP_L.toEngineTurnType())
        assertEquals(TurnType.RIGHT, NaviTurnType.TURN_SHARP_R.toEngineTurnType())
        assertEquals(TurnType.UTURN, NaviTurnType.TURN_UTURN.toEngineTurnType())
        assertEquals(TurnType.STRAIGHT, NaviTurnType.TURN_STRAIGHT.toEngineTurnType())
        assertEquals(TurnType.OTHER, NaviTurnType.TURN_KEEP_L.toEngineTurnType())
        assertEquals(TurnType.RAMP_LEFT, NaviTurnType.TURN_ENTER_IC_L.toEngineTurnType())
        assertEquals(TurnType.RAMP_LEFT, NaviTurnType.TURN_EXIT_IC_L.toEngineTurnType())
        assertEquals(TurnType.RAMP_RIGHT, NaviTurnType.TURN_ENTER_IC_R.toEngineTurnType())
        assertEquals(TurnType.RAMP_RIGHT, NaviTurnType.TURN_EXIT_IC_R.toEngineTurnType())
        assertEquals(TurnType.OTHER, NaviTurnType.TURN_EXIT_IC_C.toEngineTurnType())
        assertEquals(TurnType.TUNNEL, NaviTurnType.TURN_TUNNEL.toEngineTurnType())
    }

    @Test
    fun `와이퍼는 스위치 자동이면 AUTO, 상태 켜짐·꺼짐은 그대로, 그 밖은 모름`() {
        assertEquals(WiperMode.AUTO, wiperOf(WiperState.OFF, WiperSwitch.AUTO))
        assertEquals(WiperMode.ON, wiperOf(WiperState.ON, WiperSwitch.CONTINUOUS_LEVEL_2))
        assertEquals(WiperMode.OFF, wiperOf(WiperState.OFF, WiperSwitch.OFF))
        assertNull(wiperOf(WiperState.SERVICE, null))
        assertNull(wiperOf(null, null))
    }

    @Test
    fun `전조등은 상태가 켜짐이면 ON, 스위치가 자동이면 AUTO, 주간주행등은 OFF`() {
        assertEquals(HeadlightState.ON, headlightOf(LightState.ON, LightSwitch.AUTOMATIC))
        assertEquals(HeadlightState.AUTO, headlightOf(LightState.OFF, LightSwitch.AUTOMATIC))
        assertEquals(HeadlightState.OFF, headlightOf(LightState.DAYTIME_RUNNING, LightSwitch.DAYTIME_RUNNING))
        assertEquals(HeadlightState.OFF, headlightOf(LightState.OFF, null))
        assertNull(headlightOf(null, LightSwitch.AUTOMATIC))
    }
}
