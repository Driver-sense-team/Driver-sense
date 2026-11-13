package kkam.engine.rules

import kkam.engine.Engine
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 시나리오 S12가 다루지 않는 R4 전이표의 행과 출력 필드를 확인한다. */
class R4RuleTest {
    @Test
    fun laneGuideDistanceGrowsWithSpeedUpToMax() {
        // 36km/h는 초속 10m라서 100 + 10 × 4 = 140
        assertEquals(140.0, laneGuideDistance(36.0))
        assertEquals(100.0, laneGuideDistance(0.0))
        assertEquals(300.0, laneGuideDistance(250.0))
    }

    @Test
    fun guideTextAndActionFollowRampDirection() {
        val left = firstGuide(TurnType.RAMP_LEFT)
        val right = firstGuide(TurnType.RAMP_RIGHT)

        assertEquals("왼쪽 나들목이 곧이에요, 깜빡이 켜세요", left.text)
        assertEquals(GuideAction.SIGNAL_LEFT, left.action)
        assertEquals("오른쪽 나들목이 곧이에요, 깜빡이 켜세요", right.text)
        assertEquals(GuideAction.SIGNAL_RIGHT, right.action)
    }

    @Test
    fun keptEventUsesPlainDirectionAndNoGuideWhenSignalAlreadyOn() {
        val engine = Engine()
        // 전이표 #1: 300m 안으로 들어옴
        val start = engine.onFrame(frame(t = 0, turn = TurnType.RAMP_LEFT, distanceM = 280, signal = TurnSignal.LEFT))
        // 깜빡이가 이미 켜져 있으므로 #5 안내가 나오지 않는다
        val middle = engine.onFrame(frame(t = 4000, turn = TurnType.RAMP_LEFT, distanceM = 180, signal = TurnSignal.LEFT))
        // 전이표 #3
        val check = engine.onFrame(frame(t = 8000, turn = TurnType.RAMP_LEFT, distanceM = 90, signal = TurnSignal.LEFT))

        assertEquals(emptyList(), start)
        assertEquals(emptyList(), middle)
        val event = assertIs<Event>(check.single())
        assertEquals(RuleId.R4, event.rule)
        assertEquals(Result.KEPT, event.result)
        assertEquals(false, event.guided)
        assertEquals(TurnType.LEFT, event.turn)
    }

    @Test
    fun signalOffAtCheckPointRecordsNothing() {
        val outputs = drive(
            frame(t = 0, turn = TurnType.RAMP_RIGHT, distanceM = 280),
            frame(t = 4000, turn = TurnType.RAMP_RIGHT, distanceM = 180),
            frame(t = 8000, turn = TurnType.RAMP_RIGHT, distanceM = 90),
            frame(t = 10000, turn = TurnType.STRAIGHT, distanceM = 500, signal = TurnSignal.RIGHT),
        )

        // 안내는 나오지만 전이표 #4라서 EVENT는 없다 (MVP 단순화)
        assertEquals(listOf(RuleId.R4), outputs.map { it.rule })
        assertIs<Guide>(outputs.single())
    }

    @Test
    fun hazardDoesNotCountAsRampSignal() {
        val outputs = drive(
            frame(t = 0, turn = TurnType.RAMP_RIGHT, distanceM = 280, signal = TurnSignal.HAZARD),
            frame(t = 8000, turn = TurnType.RAMP_RIGHT, distanceM = 90, signal = TurnSignal.HAZARD),
        )

        assertTrue(outputs.none { it is Event })
    }

    @Test
    fun rerouteCancelsWatching() {
        val outputs = drive(
            frame(t = 0, turn = TurnType.RAMP_RIGHT, distanceM = 280),
            // 전이표 #2: 다른 안내로 바뀜
            frame(t = 2000, turn = TurnType.STRAIGHT, distanceM = 800),
            frame(t = 6000, turn = TurnType.STRAIGHT, distanceM = 150, signal = TurnSignal.RIGHT),
            frame(t = 9000, turn = TurnType.STRAIGHT, distanceM = 90, signal = TurnSignal.RIGHT),
        )

        assertEquals(emptyList(), outputs)
    }

    @Test
    fun parkingCancelsWatching() {
        val outputs = drive(
            frame(t = 0, turn = TurnType.RAMP_RIGHT, distanceM = 280),
            frame(t = 2000, turn = TurnType.RAMP_RIGHT, distanceM = 150, speedKph = 60.0),
            // 전이표 #0: guided도 함께 사라진다
            frame(t = 3000, turn = TurnType.RAMP_RIGHT, distanceM = 150, speedKph = 0.0, gear = Gear.P),
            // 다시 감시를 시작하고(#1) 다음 프레임에서 또 안내한다(#5)
            frame(t = 4000, turn = TurnType.RAMP_RIGHT, distanceM = 150, speedKph = 60.0),
            frame(t = 5000, turn = TurnType.RAMP_RIGHT, distanceM = 140, speedKph = 60.0),
        )

        assertEquals(listOf(2000L, 5000L), outputs.map { assertIs<Guide>(it).t })
    }

    @Test
    fun otherNaviTypesAreIgnored() {
        for (turn in listOf(TurnType.OTHER, TurnType.TUNNEL, TurnType.STRAIGHT)) {
            val outputs = drive(
                frame(t = 0, turn = turn, distanceM = 280),
                frame(t = 4000, turn = turn, distanceM = 150),
                frame(t = 8000, turn = turn, distanceM = 90),
            )
            assertEquals(emptyList(), outputs, "$turn")
        }
    }

    private fun firstGuide(turn: TurnType): Guide {
        val outputs = drive(
            frame(t = 0, turn = turn, distanceM = 280),
            frame(t = 4000, turn = turn, distanceM = 150, speedKph = 60.0),
        )
        return assertIs<Guide>(outputs.single())
    }

    private fun drive(vararg frames: SignalFrame) =
        Engine().let { engine -> frames.flatMap { engine.onFrame(it) } }

    private fun frame(
        t: Long,
        turn: TurnType,
        distanceM: Int,
        signal: TurnSignal = TurnSignal.OFF,
        speedKph: Double = 80.0,
        gear: Gear = Gear.D,
    ) = SignalFrame(
        t = t,
        speedKph = speedKph,
        gear = gear,
        steeringDeg = 0.0,
        turnSignal = signal,
        brake = false,
        lat = 37.55,
        lon = 127.07,
        headingDeg = 90.0,
        tbt = Tbt(turn, distanceM),
        light = null,
    )
}
