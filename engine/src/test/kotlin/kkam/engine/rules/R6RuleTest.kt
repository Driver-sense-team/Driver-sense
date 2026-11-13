package kkam.engine.rules

import kkam.engine.Engine
import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.Result
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kkam.engine.model.WiperMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** 시나리오 S17이 다루지 않는 R6 전이표의 행과 출력 필드를 확인한다. */
class R6RuleTest {
    @Test
    fun guideAndEventFields() {
        val outputs = drive(
            frame(t = 0, rain = true, wiper = WiperMode.OFF),
            frame(t = 5000, rain = true, wiper = WiperMode.OFF),
            frame(t = 6000, rain = true, wiper = WiperMode.AUTO),
        )

        val guide = assertIs<Guide>(outputs[0])
        assertEquals("비가 와요, 와이퍼 켜세요", guide.text)
        assertEquals(GuideAction.WIPER_ON, guide.action)
        val event = assertIs<Event>(outputs[1])
        assertEquals(Result.KEPT, event.result)
        assertEquals(true, event.guided)
        assertNull(event.turn)
    }

    @Test
    fun shortRainIsNotGuided() {
        // 전이표 #2: 5초가 되기 전에 비가 그침
        val outputs = drive(
            frame(t = 0, rain = true, wiper = WiperMode.OFF),
            frame(t = 3000, rain = false, wiper = WiperMode.OFF),
            frame(t = 6000, rain = false, wiper = WiperMode.OFF),
        )

        assertEquals(emptyList(), outputs)
    }

    @Test
    fun wiperAlreadyOnIsQuiet() {
        val outputs = drive(
            frame(t = 0, rain = true, wiper = WiperMode.ON),
            frame(t = 10000, rain = true, wiper = WiperMode.ON),
        )

        assertEquals(emptyList(), outputs)
    }

    @Test
    fun stoppedCarDoesNotStartTimer() {
        // 전이표 #1은 주행 중(speedKph > 0)에만 맞는다
        val outputs = drive(
            frame(t = 0, rain = true, wiper = WiperMode.OFF, speedKph = 0.0),
            frame(t = 6000, rain = true, wiper = WiperMode.OFF, speedKph = 0.0),
        )

        assertEquals(emptyList(), outputs)
    }

    @Test
    fun unknownValuesDoNothing() {
        val outputs = drive(
            frame(t = 0, rain = null, wiper = WiperMode.OFF),
            frame(t = 6000, rain = null, wiper = WiperMode.OFF),
            frame(t = 7000, rain = true, wiper = null),
            frame(t = 13000, rain = true, wiper = null),
        )

        assertEquals(emptyList(), outputs)
    }

    @Test
    fun nextRainGetsItsOwnGuide() {
        val outputs = drive(
            frame(t = 0, rain = true, wiper = WiperMode.OFF),
            frame(t = 5000, rain = true, wiper = WiperMode.OFF),
            frame(t = 6000, rain = true, wiper = WiperMode.ON),
            // 전이표 #6: 비가 그치면 다음 비를 기다린다
            frame(t = 20000, rain = false, wiper = WiperMode.OFF),
            frame(t = 30000, rain = true, wiper = WiperMode.OFF),
            frame(t = 35000, rain = true, wiper = WiperMode.OFF),
        )

        assertEquals(listOf("GUIDE", "EVENT", "GUIDE"), outputs.map { if (it is Guide) "GUIDE" else "EVENT" })
    }

    @Test
    fun parkingCancels() {
        val outputs = drive(
            frame(t = 0, rain = true, wiper = WiperMode.OFF),
            frame(t = 5000, rain = true, wiper = WiperMode.OFF),
            // 전이표 #0
            frame(t = 6000, rain = true, wiper = WiperMode.ON, gear = Gear.P),
        )

        assertEquals(listOf("GUIDE"), outputs.map { if (it is Guide) "GUIDE" else "EVENT" })
    }

    private fun drive(vararg frames: SignalFrame): List<EngineOutput> =
        Engine().let { engine -> frames.flatMap { engine.onFrame(it) } }

    private fun frame(
        t: Long,
        rain: Boolean?,
        wiper: WiperMode?,
        speedKph: Double = 50.0,
        gear: Gear = Gear.D,
    ) = SignalFrame(
        t = t,
        speedKph = speedKph,
        gear = gear,
        steeringDeg = 0.0,
        turnSignal = TurnSignal.OFF,
        brake = false,
        lat = 37.55,
        lon = 127.07,
        headingDeg = 90.0,
        tbt = Tbt(TurnType.STRAIGHT, 900),
        light = null,
        wiper = wiper,
        rain = rain,
    )
}
