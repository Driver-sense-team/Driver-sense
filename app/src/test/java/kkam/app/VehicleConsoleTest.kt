package kkam.app

import kkam.engine.model.Gear
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import kotlin.test.Test
import kotlin.test.assertEquals

class VehicleConsoleTest {
    private fun frame(gear: Gear, kph: Double, signal: TurnSignal = TurnSignal.OFF, brake: Boolean = false) =
        SignalFrame(0, kph, gear, 0.0, signal, brake, 37.4, 127.1, 0.0, null, null)

    @Test
    fun `첫 프레임은 기어, 속도, 깜빡이를 모두 보낸다`() {
        assertEquals(
            listOf(
                "/vehicle/gear?gear=Drive",
                "/vehicle/controls?speed=40&accelerator=20&brake=0&steering=0",
                "/vehicle/turn-signal?mode=BlinkLeft",
            ),
            consoleWrites(null, frame(Gear.D, 40.0, TurnSignal.LEFT), sendTurnSignal = true),
        )
    }

    @Test
    fun `바뀐 값만 보낸다`() {
        val a = frame(Gear.D, 40.2)
        assertEquals(emptyList(), consoleWrites(a, frame(Gear.D, 39.8)))
        assertEquals(
            listOf("/vehicle/turn-signal?mode=BlinkRight"),
            consoleWrites(a, frame(Gear.D, 40.0, TurnSignal.RIGHT), sendTurnSignal = true),
        )
        assertEquals(
            listOf("/vehicle/controls?speed=15&accelerator=0&brake=30&steering=0"),
            consoleWrites(a, frame(Gear.D, 15.0, brake = true)),
        )
    }

    @Test
    fun `속도를 보내지 않으면 속도 0에 브레이크를 밟아 차를 정지 상태로 둔다`() {
        assertEquals(
            listOf("/vehicle/gear?gear=Drive", "/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0"),
            consoleWrites(null, frame(Gear.D, 40.0), sendSpeed = false),
        )
        assertEquals(emptyList(), consoleWrites(frame(Gear.D, 40.0), frame(Gear.D, 25.0, brake = true), sendSpeed = false))
    }

    @Test
    fun `깜빡이는 기본으로 보내지 않는다 (사각지대 카메라가 앱 지도를 가리지 않게)`() {
        assertEquals(emptyList(), consoleWrites(frame(Gear.D, 40.0), frame(Gear.D, 40.0, TurnSignal.LEFT)))
    }

    @Test
    fun `기어 P로 바꾸면 기어와 속도 0을 보낸다`() {
        assertEquals(
            listOf("/vehicle/gear?gear=Park", "/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0"),
            consoleWrites(frame(Gear.D, 5.0), frame(Gear.P, 0.0, brake = true)),
        )
    }
}
