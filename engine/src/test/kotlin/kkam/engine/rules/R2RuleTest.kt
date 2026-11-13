package kkam.engine.rules

import kkam.engine.Engine
import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 시나리오 S18~S21이 다루지 않는 R2 판정 순서의 행과 출력 필드를 확인한다. */
class R2RuleTest {
    @Test
    fun rightTurnFields() {
        // 진행 방향이 커지면(시계 방향) 우회전
        val event = assertIs<Event>(
            r2(
                frame(t = 0, headingDeg = 90.0),
                frame(t = 3000, headingDeg = 120.0),
                frame(t = 6000, headingDeg = 150.0),
            ).single(),
        )

        assertEquals(RuleId.R2, event.rule)
        assertEquals(TurnType.RIGHT, event.turn)
        assertEquals(Result.MISSED, event.result)
        assertEquals(false, event.guided)
        assertEquals(6000, event.t)
    }

    @Test
    fun wrapsAroundNorth() {
        // 350° → 50°는 60° 우회전
        val event = assertIs<Event>(
            r2(frame(t = 0, headingDeg = 350.0), frame(t = 3000, headingDeg = 20.0), frame(t = 6000, headingDeg = 50.0)).single(),
        )
        assertEquals(TurnType.RIGHT, event.turn)
    }

    @Test
    fun fastTurnIsNotCounted() {
        // 구간 중 한 프레임이라도 30km/h를 넘으면 회전으로 보지 않는다
        val outputs = r2(
            frame(t = 0, headingDeg = 90.0, speedKph = 40.0),
            frame(t = 3000, headingDeg = 120.0),
            frame(t = 6000, headingDeg = 150.0),
        )
        assertEquals(emptyList(), outputs)
    }

    @Test
    fun signalBeforeTurnCountsWithinLookback() {
        // 회전 시작(t=10000) 5초 전(t=6000)에 켠 깜빡이는 인정, 그보다 앞(t=0)은 인정하지 않는다
        val kept = r2(
            frame(t = 6000, headingDeg = 90.0, signal = TurnSignal.RIGHT, speedKph = 40.0),
            frame(t = 10000, headingDeg = 90.0),
            frame(t = 13000, headingDeg = 120.0),
            frame(t = 16000, headingDeg = 150.0),
        )
        val missed = r2(
            frame(t = 0, headingDeg = 90.0, signal = TurnSignal.RIGHT, speedKph = 40.0),
            frame(t = 10000, headingDeg = 90.0),
            frame(t = 13000, headingDeg = 120.0),
            frame(t = 16000, headingDeg = 150.0),
        )

        assertEquals(Result.KEPT, assertIs<Event>(kept.single()).result)
        assertEquals(Result.MISSED, assertIs<Event>(missed.single()).result)
    }

    @Test
    fun oneTurnIsCountedOnce() {
        // 판정 뒤 기록을 비우므로 계속 돌아도 다시 60°가 쌓여야 다음 회전이다
        val outputs = r2(
            frame(t = 0, headingDeg = 90.0),
            frame(t = 3000, headingDeg = 120.0),
            frame(t = 6000, headingDeg = 150.0),
            frame(t = 8000, headingDeg = 170.0),
            frame(t = 10000, headingDeg = 180.0),
        )
        assertEquals(1, outputs.size)
    }

    @Test
    fun hazardIsNotTurnSignal() {
        val event = assertIs<Event>(
            r2(
                frame(t = 0, headingDeg = 90.0, signal = TurnSignal.HAZARD),
                frame(t = 3000, headingDeg = 60.0, signal = TurnSignal.HAZARD),
                frame(t = 6000, headingDeg = 30.0, signal = TurnSignal.HAZARD),
            ).single(),
        )
        assertEquals(Result.MISSED, event.result)
    }

    @Test
    fun rampNearbyIsLeftToR4() {
        // #2: 나들목 300m 안이면 R4가 보는 구간이라 R2는 쉰다
        val ramp = Tbt(TurnType.RAMP_RIGHT, 250)
        val outputs = r2(
            frame(t = 0, headingDeg = 90.0, tbt = ramp),
            frame(t = 3000, headingDeg = 120.0, tbt = ramp),
            frame(t = 6000, headingDeg = 150.0, tbt = ramp),
        )
        assertEquals(emptyList(), outputs)
    }

    @Test
    fun parkingClearsHistory() {
        // #0: P에서는 기록을 비운다. 다시 D가 되면 60°가 새로 쌓여야 한다
        val outputs = r2(
            frame(t = 0, headingDeg = 90.0),
            frame(t = 3000, headingDeg = 120.0, gear = Gear.P),
            frame(t = 6000, headingDeg = 150.0),
        )
        assertEquals(emptyList(), outputs)
    }

    /** 엔진 전체를 돌리고 R2 출력만 남긴다. */
    private fun r2(vararg frames: SignalFrame): List<EngineOutput> =
        Engine().let { engine -> frames.flatMap { engine.onFrame(it) } }.filter { it.rule == RuleId.R2 }

    private fun frame(
        t: Long,
        headingDeg: Double,
        speedKph: Double = 15.0,
        signal: TurnSignal = TurnSignal.OFF,
        gear: Gear = Gear.D,
        tbt: Tbt? = null,
    ) = SignalFrame(
        t = t,
        speedKph = speedKph,
        gear = gear,
        steeringDeg = 0.0,
        turnSignal = signal,
        brake = false,
        lat = 37.55,
        lon = 127.07,
        headingDeg = headingDeg,
        tbt = tbt,
        light = null,
    )
}
