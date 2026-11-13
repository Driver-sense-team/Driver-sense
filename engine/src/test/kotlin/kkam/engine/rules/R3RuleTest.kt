package kkam.engine.rules

import kkam.engine.Engine
import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.LightPhase
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TrafficLight
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 시나리오 S13~S16이 다루지 않는 R3 전이표의 행과 출력 필드를 확인한다. R1 출력은 걸러서 본다. */
class R3RuleTest {
    @Test
    fun guideAndEventFields() {
        val outputs = r3(
            frame(t = 0, distanceM = 90),
            frame(t = 2000, distanceM = 60, speedKph = 32.0),
            // 30m 안에서 멈춘 프레임은 #4가 먼저 맞아 아직 APPROACHING이다
            frame(t = 4000, distanceM = 28, speedKph = 0.0),
            // 다시 움직이면 #5로 판정 단계에 들어간다
            frame(t = 5000, distanceM = 25, speedKph = 5.0),
            frame(t = 7000, distanceM = 0, speedKph = 15.0, headingDeg = 150.0, tbt = null),
        )

        val guide = assertIs<Guide>(outputs[0])
        assertEquals("전방 적색이에요, 정지선에서 멈췄다 가세요", guide.text)
        assertEquals(GuideAction.STOP, guide.action)
        val event = assertIs<Event>(outputs[1])
        assertEquals(Result.KEPT, event.result)
        assertEquals(true, event.guided)
        assertEquals(TurnType.RIGHT, event.turn)
    }

    @Test
    fun alreadyStoppedGetsNoGuide() {
        // 전이표 #4 뒤에는 #6(안내)이 막힌다
        val outputs = r3(
            frame(t = 0, distanceM = 90),
            frame(t = 2000, distanceM = 70, speedKph = 0.0),
            frame(t = 4000, distanceM = 60, speedKph = 20.0),
            frame(t = 5000, distanceM = 28, speedKph = 10.0),
            frame(t = 7000, distanceM = 0, speedKph = 15.0, headingDeg = 150.0, tbt = null),
        )

        val event = assertIs<Event>(outputs.single())
        assertEquals(Result.KEPT, event.result)
        assertEquals(false, event.guided)
    }

    @Test
    fun greenAfterStoppingStillJudges() {
        // 멈춘 뒤에는 녹색이 돼도 #3으로 취소하지 않는다
        val outputs = r3(
            frame(t = 0, distanceM = 90),
            frame(t = 2000, distanceM = 70, speedKph = 0.0),
            frame(t = 4000, distanceM = 40, speedKph = 20.0, phase = LightPhase.GREEN),
            frame(t = 5000, distanceM = 28, speedKph = 10.0, phase = LightPhase.GREEN),
            frame(t = 7000, distanceM = 0, speedKph = 15.0, headingDeg = 150.0, tbt = null),
        )

        assertEquals(Result.KEPT, assertIs<Event>(outputs.single()).result)
    }

    @Test
    fun stopAfterTurningStartedDoesNotCount() {
        // 전이표 #7: 진행 방향이 15° 이상 바뀐 뒤 멈춘 것은 인정하지 않는다
        val outputs = r3(
            frame(t = 0, distanceM = 90),
            frame(t = 2000, distanceM = 28, speedKph = 10.0),
            frame(t = 3000, distanceM = 15, speedKph = 8.0, headingDeg = 110.0),
            frame(t = 4000, distanceM = 10, speedKph = 0.0, headingDeg = 110.0),
            frame(t = 6000, distanceM = 0, speedKph = 15.0, headingDeg = 150.0, tbt = null),
        )

        assertEquals(Result.MISSED, assertIs<Event>(outputs.single()).result)
    }

    @Test
    fun rerouteCancels() {
        // 전이표 #2
        val outputs = r3(
            frame(t = 0, distanceM = 90),
            frame(t = 2000, distanceM = 200, turn = TurnType.LEFT),
            frame(t = 4000, distanceM = 28, speedKph = 10.0),
            frame(t = 6000, distanceM = 0, speedKph = 15.0, headingDeg = 150.0, tbt = null),
        )

        // 다시 IDLE에서 28m로 들어오면 감시를 시작할 뿐 판정 단계까지 가지 못한다
        assertEquals(emptyList(), outputs)
    }

    @Test
    fun parkingCancels() {
        // 전이표 #0
        val outputs = r3(
            frame(t = 0, distanceM = 90),
            frame(t = 2000, distanceM = 28, speedKph = 10.0),
            frame(t = 3000, distanceM = 28, speedKph = 0.0, gear = Gear.P),
            frame(t = 9000, distanceM = 0, speedKph = 15.0, headingDeg = 150.0, tbt = null),
        )

        assertEquals(emptyList(), outputs)
    }

    @Test
    fun noTurnTimesOut() {
        // 전이표 #9: 판정 단계에서 30초 넘게 주행해도 회전이 없으면 취소
        val outputs = r3(
            frame(t = 0, distanceM = 90),
            frame(t = 2000, distanceM = 28, speedKph = 10.0),
            frame(t = 33000, distanceM = 0, speedKph = 40.0, tbt = null),
            frame(t = 34000, distanceM = 0, speedKph = 40.0, headingDeg = 150.0, tbt = null),
        )

        assertEquals(emptyList(), outputs)
    }

    @Test
    fun unknownLightDoesNothing() {
        val outputs = r3(
            frame(t = 0, distanceM = 90, phase = LightPhase.UNKNOWN),
            frame(t = 2000, distanceM = 60, speedKph = 32.0, phase = LightPhase.UNKNOWN),
            frame(t = 4000, distanceM = 28, speedKph = 10.0, phase = LightPhase.UNKNOWN),
            frame(t = 6000, distanceM = 0, speedKph = 15.0, headingDeg = 150.0, tbt = null),
        )

        assertEquals(emptyList(), outputs)
    }

    /** 엔진 전체를 돌리고 R3 출력만 남긴다. */
    private fun r3(vararg frames: SignalFrame): List<EngineOutput> =
        Engine().let { engine -> frames.flatMap { engine.onFrame(it) } }.filter { it.rule == RuleId.R3 }

    private fun frame(
        t: Long,
        distanceM: Int,
        speedKph: Double = 38.0,
        headingDeg: Double = 90.0,
        phase: LightPhase = LightPhase.RED,
        turn: TurnType = TurnType.RIGHT,
        gear: Gear = Gear.D,
        tbt: Tbt? = Tbt(turn, distanceM),
    ) = SignalFrame(
        t = t,
        speedKph = speedKph,
        gear = gear,
        steeringDeg = 0.0,
        turnSignal = TurnSignal.RIGHT,
        brake = false,
        lat = 37.55,
        lon = 127.07,
        headingDeg = headingDeg,
        tbt = tbt,
        light = TrafficLight(phase, null),
    )
}
