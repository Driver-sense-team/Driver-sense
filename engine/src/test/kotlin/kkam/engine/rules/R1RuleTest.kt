package kkam.engine.rules

import kkam.engine.Engine
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.Result
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class R1RuleTest {
    @Test
    fun headingDiffHandlesExamplesFromSpec() {
        assertEquals(60.0, headingDiff(90.0, 30.0))
        assertEquals(20.0, headingDiff(350.0, 10.0))
        assertEquals(20.0, headingDiff(10.0, 350.0))
    }

    @Test
    fun guideDistanceStaysWithinLimits() {
        assertEquals(50.0, guideDistance(0.0))
        assertEquals(50.0, guideDistance(15.0))
        assertEquals(100.0, guideDistance(80.0))
    }

    @Test
    fun guideDistanceGrowsWithSpeed() {
        // 36km/h는 초속 10m라서 30 + 10 × 4 = 70
        assertEquals(70.0, guideDistance(36.0))
    }

    // 시나리오 비교에서 제외되는 text, id, turn, 위치를 여기서 확인한다.

    @Test
    fun guideTextFollowsTurnDirection() {
        assertEquals("좌회전 곧이에요, 깜빡이 켜세요", firstGuide(TurnType.LEFT).text)
        assertEquals("우회전 곧이에요, 깜빡이 켜세요", firstGuide(TurnType.RIGHT).text)
    }

    @Test
    fun guideActionFollowsTurnDirection() {
        assertEquals(GuideAction.SIGNAL_LEFT, firstGuide(TurnType.LEFT).action)
        assertEquals(GuideAction.SIGNAL_RIGHT, firstGuide(TurnType.RIGHT).action)
    }

    @Test
    fun eventCarriesTurnAndFramePosition() {
        val event = driveTurn(Engine(), TurnType.RIGHT, signal = TurnSignal.RIGHT, startT = 0)

        assertEquals("evt-1", event.id)
        assertEquals(Result.KEPT, event.result)
        assertEquals(TurnType.RIGHT, event.turn)
        assertEquals(FINISH_LAT, event.lat)
        assertEquals(FINISH_LON, event.lon)
    }

    @Test
    fun eventIdsIncreaseInOneEngine() {
        val engine = Engine()

        val first = driveTurn(engine, TurnType.LEFT, signal = TurnSignal.LEFT, startT = 0)
        val second = driveTurn(engine, TurnType.LEFT, signal = TurnSignal.LEFT, startT = 10000)

        assertEquals("evt-1", first.id)
        assertEquals("evt-2", second.id)
    }

    @Test
    fun uTurnAsksForLeftSignal() {
        val guide = firstGuide(TurnType.UTURN)

        assertEquals("유턴 곧이에요, 왼쪽 깜빡이 켜세요", guide.text)
        assertEquals(GuideAction.SIGNAL_LEFT, guide.action)
    }

    @Test
    fun uTurnEventKeepsUTurnAndNeedsLeftSignal() {
        val kept = driveTurn(Engine(), TurnType.UTURN, signal = TurnSignal.LEFT, startT = 0)
        val missed = driveTurn(Engine(), TurnType.UTURN, signal = TurnSignal.RIGHT, startT = 0)

        assertEquals(TurnType.UTURN, kept.turn)
        assertEquals(Result.KEPT, kept.result)
        assertEquals(Result.MISSED, missed.result)
    }

    @Test
    fun hazardDoesNotCountAsTurnSignal() {
        val event = driveTurn(Engine(), TurnType.LEFT, signal = TurnSignal.HAZARD, startT = 0)

        assertEquals(Result.MISSED, event.result)
    }

    @Test
    fun timeoutStartsOnlyAfterExactlyThirtySecondsOfDriving() {
        val engine = Engine()
        engine.onFrame(frame(t = 0, turn = TurnType.LEFT, distanceM = 90, signal = TurnSignal.LEFT))
        engine.onFrame(frame(t = 1000, turn = TurnType.LEFT, distanceM = 20, signal = TurnSignal.LEFT))

        // 주행 시간이 정확히 30000ms다. 초과가 아니므로 아직 취소되지 않는다.
        val atLimit = engine.onFrame(frame(t = 31000, turn = TurnType.LEFT, distanceM = 15))
        // 정차 중이라 주행 시간은 그대로이고, 여기서 회전이 끝난다.
        val finished = engine.onFrame(
            frame(t = 32000, turn = TurnType.LEFT, distanceM = 15, headingDeg = 30.0, speedKph = 0.0),
        )

        assertEquals(emptyList(), atLimit)
        assertIs<Event>(finished.single())
    }

    private fun firstGuide(turn: TurnType): Guide {
        val engine = Engine()
        engine.onFrame(frame(t = 0, turn = turn, distanceM = 90))
        return assertIs<Guide>(engine.onFrame(frame(t = 1000, turn = turn, distanceM = 45)).single())
    }

    /** 감시 시작, 판정, 회전 완료까지 프레임 세 개를 넣고 EVENT를 돌려준다. */
    private fun driveTurn(engine: Engine, turn: TurnType, signal: TurnSignal, startT: Long): Event {
        engine.onFrame(frame(t = startT, turn = turn, distanceM = 90, signal = signal))
        engine.onFrame(frame(t = startT + 1000, turn = turn, distanceM = 20, signal = signal))
        val outputs = engine.onFrame(
            frame(
                t = startT + 2000,
                turn = TurnType.STRAIGHT,
                distanceM = 500,
                signal = signal,
                headingDeg = 30.0,
                lat = FINISH_LAT,
                lon = FINISH_LON,
            ),
        )
        return assertIs<Event>(outputs.single())
    }

    private fun frame(
        t: Long,
        turn: TurnType,
        distanceM: Int,
        signal: TurnSignal = TurnSignal.OFF,
        headingDeg: Double = 90.0,
        lat: Double = 37.55,
        lon: Double = 127.07,
        speedKph: Double = 20.0,
    ) = SignalFrame(
        t = t,
        speedKph = speedKph,
        gear = Gear.D,
        steeringDeg = 0.0,
        turnSignal = signal,
        brake = false,
        lat = lat,
        lon = lon,
        headingDeg = headingDeg,
        tbt = Tbt(turn, distanceM),
        light = null,
    )

    private companion object {
        const val FINISH_LAT = 37.5512
        const val FINISH_LON = 127.0734
    }
}
