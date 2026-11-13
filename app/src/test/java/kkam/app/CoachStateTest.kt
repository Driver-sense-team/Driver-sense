package kkam.app

import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.HeadlightState
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlin.test.Test
import kotlin.test.assertEquals

class CoachStateTest {
    private fun frame(signal: TurnSignal, tbt: Tbt?, headlight: HeadlightState? = null) = SignalFrame(
        t = 4000,
        speedKph = 30.0,
        gear = Gear.D,
        steeringDeg = 0.0,
        turnSignal = signal,
        brake = false,
        lat = 37.55,
        lon = 127.07,
        headingDeg = 90.0,
        tbt = tbt,
        light = null,
        headlight = headlight,
    )

    private fun event(result: Result, guided: Boolean) =
        Event("evt-1", 11000, RuleId.R1, result, guided, TurnType.LEFT, 37.55, 127.07)

    @Test
    fun `조용히 하면 카드는 대기로 두고 판정 기록은 쌓는다`() {
        val near = frame(TurnSignal.OFF, Tbt(TurnType.LEFT, 48))
        val state = CoachState().next(near, listOf(event(Result.MISSED, guided = true)), quiet = true)

        assertEquals(Tone.IDLE, state.tone)
        assertEquals(1, state.total)
        assertEquals(0, state.kept)
        assertEquals(1, state.todayEvents.size)
    }

    @Test
    fun `깜빡이가 꺼져 있으면 주황으로 켜라고 한다`() {
        val state = CoachState().next(frame(TurnSignal.OFF, Tbt(TurnType.LEFT, 48)), emptyList())

        assertEquals(Tone.WARN, state.tone)
        assertEquals("좌회전", state.turnLabel)
        assertEquals("48 m", state.distanceLabel)
        assertEquals("깜빡이 꺼짐", state.signalLabel)
        assertEquals("지금 켜세요", state.badge)
    }

    @Test
    fun `같은 방향 깜빡이는 파랑이고 반대 방향은 주황이다`() {
        val same = CoachState().next(frame(TurnSignal.LEFT, Tbt(TurnType.LEFT, 48)), emptyList())
        val opposite = CoachState().next(frame(TurnSignal.RIGHT, Tbt(TurnType.LEFT, 48)), emptyList())

        assertEquals(Tone.OK, same.tone)
        assertEquals("좋아요", same.badge)
        assertEquals(Tone.WARN, opposite.tone)
        assertEquals("반대쪽 깜빡이", opposite.signalLabel)
    }

    @Test
    fun `다음 안내가 회전이 아니면 대기로 돌아간다`() {
        val state = CoachState().next(frame(TurnSignal.OFF, Tbt(TurnType.STRAIGHT, 500)), emptyList())

        assertEquals(Tone.IDLE, state.tone)
        assertEquals("다음 회전 대기", state.turnLabel)
    }

    @Test
    fun `안내와 판정을 문구와 횟수와 기록에 반영한다`() {
        val quiet = frame(TurnSignal.LEFT, null)
        val guide = Guide(5000, RuleId.R1, "좌회전 곧이에요, 깜빡이 켜세요", GuideAction.SIGNAL_LEFT)

        val guided = CoachState().next(quiet, listOf(guide))
        assertEquals(guide.text, guided.message)

        val kept = guided.next(quiet, listOf(event(Result.KEPT, guided = true)))
        assertEquals("안내 듣고 잘 켰어요", kept.message)
        assertEquals(1 to 1, kept.kept to kept.total)
        assertEquals(listOf("5초 음성 안내", "11초 좌회전 깜빡이 지킴"), kept.logs)

        val missed = kept.next(quiet, listOf(event(Result.MISSED, guided = true)))
        assertEquals("이번 좌회전은 깜빡이를 놓쳤어요", missed.message)
        assertEquals(1 to 2, missed.kept to missed.total)
    }

    @Test
    fun `실제 주행의 기록은 한국 시각으로 표시한다`() {
        val guide = Guide(1_793_897_400_000, RuleId.R1, "안내", GuideAction.SIGNAL_LEFT)

        val state = CoachState().resetDrive("실차").next(frame(TurnSignal.OFF, null), listOf(guide))

        assertEquals(listOf("실차 01:50:00 음성 안내"), state.logs)
    }

    @Test
    fun `새 주행은 카드만 비우고 횟수와 기록은 남긴다`() {
        val state = CoachState(tone = Tone.WARN, message = "안내", kept = 1, total = 2, logs = listOf("기록"))

        val reset = state.resetDrive("S2")

        assertEquals(Tone.IDLE, reset.tone)
        assertEquals("", reset.message)
        assertEquals(1 to 2, reset.kept to reset.total)
        assertEquals(listOf("기록"), reset.logs)
    }

    @Test
    fun `깜빡이를 켜면 안내 문구가 바뀌고 켜둔 채 시작한 주행에는 문구가 없다`() {
        val guide = Guide(4000, RuleId.R1, "좌회전 곧이에요, 깜빡이 켜세요", GuideAction.SIGNAL_LEFT)
        val near = Tbt(TurnType.LEFT, 48)

        val guided = CoachState().resetDrive("S2").next(frame(TurnSignal.OFF, near), listOf(guide))
        val answered = guided.next(frame(TurnSignal.LEFT, near), emptyList())
        assertEquals("깜빡이를 켰어요", answered.message)
        assertEquals(listOf("S2 4초 음성 안내"), answered.logs)

        val quiet = CoachState().resetDrive("S1").next(frame(TurnSignal.LEFT, near), emptyList())
        assertEquals("", quiet.message)
    }

    @Test
    fun `감시 거리 밖의 회전에는 주황을 띄우지 않는다`() {
        val far = CoachState().next(frame(TurnSignal.OFF, Tbt(TurnType.LEFT, 400)), emptyList())

        assertEquals(Tone.IDLE, far.tone)
    }

    @Test
    fun `판정 지점을 지난 뒤 깜빡이를 켜도 좋아요로 바꾸지 않는다`() {
        val guide = Guide(4000, RuleId.R1, "좌회전 곧이에요, 깜빡이 켜세요", GuideAction.SIGNAL_LEFT)

        val guided = CoachState().next(frame(TurnSignal.OFF, Tbt(TurnType.LEFT, 60)), listOf(guide))
        // 30m 지점을 깜빡이 없이 지남. 엔진은 여기서 놓침으로 정한다
        val passed = guided.next(frame(TurnSignal.OFF, Tbt(TurnType.LEFT, 28)), emptyList())
        val late = passed.next(frame(TurnSignal.LEFT, Tbt(TurnType.LEFT, 15)), emptyList())

        assertEquals(Tone.WARN, late.tone)
        assertEquals("다음엔 미리 켜요", late.badge)
        assertEquals("깜빡이 켜짐", late.signalLabel)
        assertEquals(guide.text, late.message)
    }

    @Test
    fun `판정 지점 전에 켰으면 지난 뒤에도 파랑이다`() {
        val onTime = CoachState().next(frame(TurnSignal.LEFT, Tbt(TurnType.LEFT, 28)), emptyList())
        val after = onTime.next(frame(TurnSignal.OFF, Tbt(TurnType.LEFT, 15)), emptyList())

        assertEquals(Tone.OK, after.tone)
    }

    @Test
    fun `나들목은 차선 변경 카드이고 오른쪽 방향이다`() {
        val state = CoachState().next(frame(TurnSignal.OFF, Tbt(TurnType.RAMP_RIGHT, 167)), emptyList())

        assertEquals(Tone.WARN, state.tone)
        assertEquals(GuideKind.LANE_CHANGE, state.guideKind)
        assertEquals(true, state.right)
        assertEquals("오른쪽 나들목", state.turnLabel)
    }

    @Test
    fun `터널은 전조등으로 판단하고 전조등 값이 없으면 대기다`() {
        val off = CoachState().next(frame(TurnSignal.OFF, Tbt(TurnType.TUNNEL, 187), HeadlightState.OFF), emptyList())
        val auto = CoachState().next(frame(TurnSignal.OFF, Tbt(TurnType.TUNNEL, 187), HeadlightState.AUTO), emptyList())
        val unknown = CoachState().next(frame(TurnSignal.OFF, Tbt(TurnType.TUNNEL, 187)), emptyList())

        assertEquals(GuideKind.HEADLIGHT, off.guideKind)
        assertEquals(Tone.WARN to "전조등 켜세요", off.tone to off.badge)
        assertEquals(Tone.OK to "전조등 자동", auto.tone to auto.signalLabel)
        assertEquals(Tone.IDLE, unknown.tone)
    }

    @Test
    fun `터널 판정은 방향 없이 기록한다`() {
        val event = Event("evt-1", 14000, RuleId.R5, Result.KEPT, true, null, 37.55, 127.07)

        val state = CoachState().next(frame(TurnSignal.OFF, null), listOf(event))

        assertEquals("안내 듣고 전조등을 켰어요", state.message)
        assertEquals(listOf("14초 터널 전조등 지킴"), state.logs)
    }

    @Test
    fun `적색 우회전 판정 문구`() {
        val kept = Event("evt-1", 14000, RuleId.R3, Result.KEPT, true, TurnType.RIGHT, 37.55, 127.07)

        val state = CoachState().next(frame(TurnSignal.RIGHT, null), listOf(kept, kept.copy(result = Result.MISSED)))

        assertEquals(listOf("14초 적색 우회전 일시정지 지킴", "14초 적색 우회전 일시정지 놓침"), state.logs)
        assertEquals("이번 우회전은 멈추지 않았어요", state.message)
    }
}
