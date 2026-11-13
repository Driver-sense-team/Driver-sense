package kkam.engine.rules

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 시나리오 S30~S32가 다루지 않는 R8 전이표의 행과 출력 필드를 확인한다 (spec 14.3). */
class R8RuleTest {
    private var count = 0
    private val rule = R8Rule { "evt-${++count}" }

    private fun frame(
        t: Long,
        turn: TurnType?,
        distanceM: Int = 0,
        signal: TurnSignal = TurnSignal.OFF,
        gear: Gear = Gear.D,
    ) = SignalFrame(t, 20.0, gear, 0.0, signal, false, 37.55, 127.07, 0.0, turn?.let { Tbt(it, distanceM) }, null)

    private fun run(vararg frames: SignalFrame): List<EngineOutput> = frames.flatMap { rule.onFrame(it) }

    @Test
    fun `진입은 왼쪽, 진출은 오른쪽 깜빡이를 안내한다`() {
        val guides = run(
            frame(0, TurnType.ROUNDABOUT_ENTER, 80),
            frame(1000, TurnType.ROUNDABOUT_ENTER, 45),
            frame(2000, TurnType.ROUNDABOUT_ENTER, 12, TurnSignal.OFF),
            frame(3000, TurnType.ROUNDABOUT_EXIT, 40),
            frame(4000, TurnType.ROUNDABOUT_EXIT, 30),
        ).filterIsInstance<Guide>()

        assertEquals(listOf(R8Rule.ENTER_TEXT, R8Rule.EXIT_TEXT), guides.map { it.text })
        assertEquals(listOf(GuideAction.SIGNAL_LEFT, GuideAction.SIGNAL_RIGHT), guides.map { it.action })
        assertTrue(guides.all { it.rule == RuleId.R8 })
    }

    @Test
    fun `판정 지점(진입 30m)을 지나서 켜면 놓침이다 - 한 번 본 깜빡이는 바꾸지 않는다`() {
        val out = run(
            frame(0, TurnType.ROUNDABOUT_ENTER, 80),
            frame(1000, TurnType.ROUNDABOUT_ENTER, 28, TurnSignal.OFF),
            frame(2000, TurnType.ROUNDABOUT_ENTER, 10, TurnSignal.LEFT),
            frame(3000, TurnType.ROUNDABOUT_EXIT, 40, TurnSignal.LEFT),
        )
        val event = assertIs<Event>(out.last())
        assertEquals(Result.MISSED, event.result)
        assertEquals(TurnType.ROUNDABOUT_ENTER, event.turn)
    }

    @Test
    fun `진출은 출구 10m 앞에서 본다 - 회전교차로 안이라 30m에서는 아직 판정하지 않는다`() {
        val out = run(
            frame(0, TurnType.ROUNDABOUT_EXIT, 25, TurnSignal.OFF),
            frame(1000, TurnType.ROUNDABOUT_EXIT, 12, TurnSignal.OFF),
            frame(2000, TurnType.ROUNDABOUT_EXIT, 6, TurnSignal.RIGHT),
            frame(3000, TurnType.STRAIGHT, 500, TurnSignal.RIGHT),
        )
        val event = assertIs<Event>(out.last())
        assertEquals(Result.KEPT, event.result)
        assertEquals(TurnType.ROUNDABOUT_EXIT, event.turn)
        assertEquals(true, event.guided)
    }

    @Test
    fun `비상등은 맞는 깜빡이가 아니다`() {
        val out = run(
            frame(0, TurnType.ROUNDABOUT_ENTER, 25, TurnSignal.HAZARD),
            frame(1000, TurnType.ROUNDABOUT_ENTER, 8, TurnSignal.HAZARD),
            frame(2000, TurnType.ROUNDABOUT_EXIT, 40, TurnSignal.HAZARD),
        )
        assertEquals(Result.MISSED, assertIs<Event>(out.single()).result)
    }

    @Test
    fun `멀리서 다른 안내로 바뀌면 재탐색으로 보고 기록하지 않는다`() {
        val out = run(
            frame(0, TurnType.ROUNDABOUT_ENTER, 28, TurnSignal.LEFT),
            frame(1000, TurnType.ROUNDABOUT_ENTER, 25, TurnSignal.LEFT),
            frame(2000, TurnType.LEFT, 300, TurnSignal.LEFT),
        )
        assertTrue(out.none { it is Event })
    }

    @Test
    fun `판정 지점을 보지 못하고 통과하면 기록하지 않는다`() {
        // 진출 15m(판정 지점 10m 밖)에서 바로 다음 안내로 바뀜
        val out = run(
            frame(0, TurnType.ROUNDABOUT_EXIT, 15, TurnSignal.RIGHT),
            frame(1000, TurnType.STRAIGHT, 500, TurnSignal.RIGHT),
        )
        assertTrue(out.none { it is Event })
    }

    @Test
    fun `기어 P·R이면 감시를 취소한다`() {
        val out = run(
            frame(0, TurnType.ROUNDABOUT_ENTER, 28, TurnSignal.LEFT),
            frame(1000, TurnType.ROUNDABOUT_ENTER, 10, TurnSignal.LEFT, gear = Gear.P),
            frame(2000, TurnType.ROUNDABOUT_EXIT, 40),
        )
        assertTrue(out.none { it is Event })
    }
}
