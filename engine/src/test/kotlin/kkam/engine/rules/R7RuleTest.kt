package kkam.engine.rules

import kkam.engine.Engine
import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kkam.engine.model.WiperMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** 시나리오 S22~S24가 다루지 않는 R7 전이표의 행과 출력 필드를 확인한다. */
class R7RuleTest {
    @Test
    fun guideAndEventFields() {
        val outputs = r7(
            frame(t = 0),
            frame(t = 10000),
            frame(t = 12000, defroster = true),
        )

        val guide = assertIs<Guide>(outputs[0])
        assertEquals("앞유리가 뿌예질 수 있어요, 성에 제거를 켜고 외기로 바꾸세요", guide.text)
        assertEquals(GuideAction.DEFOG, guide.action)
        val event = assertIs<Event>(outputs[1])
        assertEquals(Result.KEPT, event.result)
        assertEquals(true, event.guided)
        assertNull(event.turn)
    }

    @Test
    fun switchingToFreshAirCounts() {
        // 전이표 #5: 안내 때 켜져 있던 내기 순환을 외기로 바꿔도 지킴
        val outputs = r7(frame(t = 0), frame(t = 10000), frame(t = 12000, recirc = false))

        assertEquals(Result.KEPT, assertIs<Event>(outputs[1]).result)
    }

    @Test
    fun waitsWhileWiperGuideIsBusy() {
        // R6가 와이퍼 안내를 진행하는 동안은 시간을 재지 않는다. 와이퍼를 켠 뒤부터 10초를 잰다
        val outputs = r7(
            frame(t = 0, wiper = WiperMode.OFF),
            frame(t = 5000, wiper = WiperMode.OFF),
            frame(t = 7000, wiper = WiperMode.ON),
            frame(t = 12000, wiper = WiperMode.ON),
            frame(t = 17000, wiper = WiperMode.ON),
        ).filter { it.rule == RuleId.R7 }

        assertEquals(17000, assertIs<Guide>(outputs.single()).t)
    }

    @Test
    fun breakInSituationRestartsTimer() {
        // 전이표 #3: 중간에 비가 그치면 처음부터 다시 잰다
        val outputs = r7(
            frame(t = 0),
            frame(t = 6000, rain = false),
            frame(t = 8000),
            frame(t = 15000),
            frame(t = 18000),
        )

        assertEquals(18000, assertIs<Guide>(outputs.single()).t)
    }

    @Test
    fun voiceRequestWithDefrosterAlreadyOnIsQuiet() {
        val outputs = r7(frame(t = 0, rain = false, recirc = false, defroster = true, fogReported = true))

        assertEquals(emptyList(), outputs)
    }

    @Test
    fun unknownDefrosterDoesNothing() {
        val outputs = r7(frame(t = 0, defroster = null), frame(t = 10000, defroster = null), frame(t = 11000, defroster = null, fogReported = true))

        assertEquals(emptyList(), outputs)
    }

    @Test
    fun guideIsNotRepeatedWhileWatching() {
        // WATCHING은 따르거나 P·R이 될 때까지 유지하므로 같은 주행에서 다시 안내하지 않는다
        val outputs = r7(frame(t = 0), frame(t = 10000), frame(t = 30000), frame(t = 40000, fogReported = true))

        assertEquals(1, outputs.size)
    }

    @Test
    fun parkingCancels() {
        val outputs = r7(frame(t = 0), frame(t = 10000), frame(t = 11000, gear = Gear.P, defroster = true))

        assertEquals(listOf("GUIDE"), outputs.map { if (it is Guide) "GUIDE" else "EVENT" })
    }

    /** 엔진 전체를 돌리고 R7 출력만 남긴다. 기본은 비 + 내기 순환 + 성에 제거 꺼짐 + 와이퍼 켜짐. */
    private fun r7(vararg frames: SignalFrame): List<EngineOutput> =
        Engine().let { engine -> frames.flatMap { engine.onFrame(it) } }.filter { it.rule == RuleId.R7 }

    private fun frame(
        t: Long,
        rain: Boolean? = true,
        recirc: Boolean? = true,
        defroster: Boolean? = false,
        wiper: WiperMode? = WiperMode.ON,
        fogReported: Boolean? = null,
        gear: Gear = Gear.D,
    ) = SignalFrame(
        t = t,
        speedKph = 40.0,
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
        defroster = defroster,
        recirc = recirc,
        fogReported = fogReported,
    )
}
