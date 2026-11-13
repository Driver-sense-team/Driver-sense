package kkam.app

import kkam.engine.Engine
import kkam.engine.model.EngineOutput
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
import kotlin.test.assertNull

class PopupPolicyTest {
    private fun frame(signal: TurnSignal, tbt: Tbt?, gear: Gear = Gear.D, headlight: HeadlightState? = null, t: Long = 4000) =
        SignalFrame(t, 30.0, gear, 0.0, signal, false, 37.55, 127.07, 90.0, tbt, null, headlight)

    private val guide = Guide(4000, RuleId.R1, "좌회전 곧이에요, 깜빡이 켜세요", GuideAction.SIGNAL_LEFT)
    private val kept = Event("evt-1", 11000, RuleId.R1, Result.KEPT, true, TurnType.LEFT, 37.55, 127.07)
    private val missed = kept.copy(result = Result.MISSED)
    private val near = Tbt(TurnType.LEFT, 60)

    /** 프레임과 엔진 출력을 [CoachState]와 팝업에 차례로 반영한다. */
    private fun step(state: CoachState, popup: Popup?, frame: SignalFrame, outputs: List<EngineOutput>): Pair<CoachState, Popup?> {
        val next = state.next(frame, outputs)
        return next to nextPopup(popup, next, outputs, frame)
    }

    @Test
    fun `음성 안내 전에는 회전이 가까워도 띄우지 않는다`() {
        val (_, popup) = step(CoachState(), null, frame(TurnSignal.OFF, Tbt(TurnType.LEFT, 90)), emptyList())

        assertNull(popup)
    }

    @Test
    fun `음성 안내가 나오면 주황, 깜빡이를 켜면 파랑으로 바뀐다`() {
        val (guided, warn) = step(CoachState(), null, frame(TurnSignal.OFF, near), listOf(guide))
        assertEquals(Popup(PopupTone.WARN, GuideKind.TURN_SIGNAL, false, "좌회전", "60 m", "지금 켜세요", TurnType.LEFT), warn)

        val (_, ok) = step(guided, warn, frame(TurnSignal.LEFT, Tbt(TurnType.LEFT, 40)), emptyList())
        assertEquals(Popup(PopupTone.OK, GuideKind.TURN_SIGNAL, false, "좌회전", "40 m", "좋아요", TurnType.LEFT), ok)
    }

    @Test
    fun `판정 지점(30m)을 꺼진 채 지나면 빨강 "다음엔 미리 켜요"로 바뀐다`() {
        val (guided, warn) = step(CoachState(), null, frame(TurnSignal.OFF, near), listOf(guide))
        val (late, miss) = step(guided, warn, frame(TurnSignal.OFF, Tbt(TurnType.LEFT, 25)), emptyList())

        assertEquals(Popup(PopupTone.MISS, GuideKind.TURN_SIGNAL, false, "좌회전", "25 m", "다음엔 미리 켜요", TurnType.LEFT), miss)
        assertEquals(CardLook.MISS, miss?.tone?.look())
        // 지나서 깜빡이를 켜도 빨강으로 남는다 (판정 지점에서 색이 고정된다)
        assertEquals(PopupTone.MISS, step(late, miss, frame(TurnSignal.LEFT, Tbt(TurnType.LEFT, 15)), emptyList()).second?.tone)
    }

    @Test
    fun `놓침이면 숨기고 지킴이면 파랑으로 바꾼다`() {
        val (guided, warn) = step(CoachState(), null, frame(TurnSignal.OFF, near), listOf(guide))

        assertNull(step(guided, warn, frame(TurnSignal.OFF, near), listOf(missed)).second)
        assertEquals(PopupTone.OK, step(guided, warn, frame(TurnSignal.LEFT, near), listOf(kept)).second?.tone)
    }

    @Test
    fun `처음부터 깜빡이를 켜고 와서 안내가 없으면 띄우지 않는다`() {
        val (_, popup) = step(CoachState(), null, frame(TurnSignal.LEFT, near), emptyList())

        assertNull(popup)
    }

    @Test
    fun `회전 안내가 끝나거나 기어가 P면 숨긴다`() {
        val (guided, warn) = step(CoachState(), null, frame(TurnSignal.OFF, near), listOf(guide))

        assertNull(step(guided, warn, frame(TurnSignal.OFF, Tbt(TurnType.STRAIGHT, 500)), emptyList()).second)
        assertNull(step(guided, warn, frame(TurnSignal.OFF, near, Gear.P), emptyList()).second)
    }

    @Test
    fun `안내 뒤 재탐색으로 다른 회전이 되면 숨긴다`() {
        val (guided, warn) = step(CoachState(), null, frame(TurnSignal.OFF, near), listOf(guide))

        val (_, popup) = step(guided, warn, frame(TurnSignal.OFF, Tbt(TurnType.RIGHT, 80)), emptyList())

        assertNull(popup)
    }

    @Test
    fun `같은 프레임에 판정과 새 안내가 오면 새 안내를 띄운다`() {
        val rampGuide = Guide(4000, RuleId.R4, "오른쪽 나들목이 곧이에요, 깜빡이 켜세요", GuideAction.SIGNAL_RIGHT)

        val (_, popup) = step(CoachState(), null, frame(TurnSignal.OFF, Tbt(TurnType.RAMP_RIGHT, 180)), listOf(kept, rampGuide))

        assertEquals(PopupTone.WARN, popup?.tone)
        assertEquals(GuideKind.LANE_CHANGE, popup?.kind)
        assertEquals(true, popup?.right)
    }

    @Test
    fun `S11 터널은 안내 뒤 전조등을 켠 프레임에 파랑이 보인다`() {
        // 엔진을 직접 돌려 실제 출력 순서(GUIDE 다음 프레임에 바로 EVENT)로 확인한다
        val engine = Engine()
        var state = CoachState()
        var popup: Popup? = null
        val tones = mutableListOf<PopupTone?>()
        for ((t, distance, light) in listOf(
            Triple(10000L, 206, HeadlightState.OFF),
            Triple(11000L, 187, HeadlightState.OFF),
            Triple(14000L, 129, HeadlightState.ON),
        )) {
            val f = frame(TurnSignal.OFF, Tbt(TurnType.TUNNEL, distance), headlight = light, t = t)
            val outputs = engine.onFrame(f)
            state = state.next(f, outputs)
            popup = nextPopup(popup, state, outputs, f)
            tones += popup?.tone
        }

        assertEquals(listOf(null, PopupTone.WARN, PopupTone.OK), tones)
        assertEquals(GuideKind.HEADLIGHT, popup?.kind)
    }

    @Test
    fun `S13 적색 우회전은 멈추라는 카드가 뜨고 멈추면 파랑으로 바뀐다`() {
        val engine = Engine()
        var state = CoachState()
        var popup: Popup? = null
        val seen = mutableListOf<Triple<PopupTone?, GuideKind?, String?>>()
        val red = kkam.engine.model.TrafficLight(kkam.engine.model.LightPhase.RED, null)
        for ((t, distance, speed) in listOf(
            Triple(2000L, 90, 38.0),
            Triple(4000L, 60, 32.0),
            Triple(7000L, 28, 10.0),
            Triple(8000L, 25, 0.0),
            Triple(12000L, 15, 10.0),
        )) {
            val f = frame(TurnSignal.RIGHT, Tbt(TurnType.RIGHT, distance), t = t).copy(speedKph = speed, light = red)
            val outputs = engine.onFrame(f)
            state = state.next(f, outputs)
            popup = nextPopup(popup, state, outputs, f)
            seen += Triple(popup?.tone, popup?.kind, popup?.headline)
        }

        assertEquals(
            listOf(
                Triple(null, null, null),
                Triple(PopupTone.WARN, GuideKind.RED_RIGHT_TURN, "멈췄다 가세요"),
                Triple(PopupTone.WARN, GuideKind.RED_RIGHT_TURN, "멈췄다 가세요"),
                Triple(PopupTone.OK, GuideKind.RED_RIGHT_TURN, "좋아요"),
                // 한 번 멈췄으면 다시 움직여도 파랑을 유지한다 (엔진도 지킴으로 본다)
                Triple(PopupTone.OK, GuideKind.RED_RIGHT_TURN, "좋아요"),
            ),
            seen,
        )
        assertEquals(true, popup?.right)
        assertEquals("정지선에서 멈췄어요", state.message)
    }

    @Test
    fun `S17 비 오는 중 와이퍼 카드가 뜨고 켜면 파랑으로 바뀐다`() {
        val engine = Engine()
        var state = CoachState()
        var popup: Popup? = null
        val seen = mutableListOf<Triple<PopupTone?, String?, String?>>()
        for ((t, rain, wiper) in listOf(
            Triple(5000L, true, kkam.engine.model.WiperMode.OFF),
            Triple(10000L, true, kkam.engine.model.WiperMode.OFF),
            Triple(13000L, true, kkam.engine.model.WiperMode.ON),
            // 판정 다음 프레임: 카드는 대기로 돌아가도 "좋아요" 팝업은 남는다 (숨기기는 CoachSession이 몇 초 뒤에)
            Triple(14000L, true, kkam.engine.model.WiperMode.ON),
        )) {
            val f = frame(TurnSignal.OFF, Tbt(TurnType.STRAIGHT, 900), t = t).copy(speedKph = 50.0, rain = rain, wiper = wiper)
            val outputs = engine.onFrame(f)
            state = state.next(f, outputs)
            popup = nextPopup(popup, state, outputs, f)
            seen += Triple(popup?.tone, popup?.turnLabel, popup?.headline)
        }

        assertEquals(
            listOf(
                Triple(null, null, null),
                Triple(PopupTone.WARN, "비 오는 중", "와이퍼 켜세요"),
                Triple(PopupTone.OK, "비 오는 중", "좋아요"),
                Triple(PopupTone.OK, "비 오는 중", "좋아요"),
            ),
            seen,
        )
        assertEquals(GuideKind.WIPER, popup?.kind)
        assertEquals(null, popup?.forTurn)
        // 와이퍼는 법규 근거가 없어 오늘 성적(지킴률)에 세지 않는다
        assertEquals(0 to 0, state.kept to state.total)
        assertEquals("안내 듣고 와이퍼를 켰어요", state.message)
    }

    @Test
    fun `S22 김서림 카드가 뜨고 성에 제거를 켜면 파랑으로 바뀐다`() {
        val engine = Engine()
        var state = CoachState()
        var popup: Popup? = null
        val seen = mutableListOf<Triple<PopupTone?, String?, String?>>()
        for ((t, defroster) in listOf(0L to false, 10000L to false, 14000L to true, 15000L to true)) {
            val f = frame(TurnSignal.OFF, Tbt(TurnType.STRAIGHT, 900), t = t).copy(
                speedKph = 40.0, rain = true, wiper = kkam.engine.model.WiperMode.ON, recirc = true, defroster = defroster,
            )
            val outputs = engine.onFrame(f)
            state = state.next(f, outputs)
            popup = nextPopup(popup, state, outputs, f)
            seen += Triple(popup?.tone, popup?.turnLabel, popup?.headline)
        }

        assertEquals(
            listOf(
                Triple(null, null, null),
                Triple(PopupTone.WARN, "김서림 주의", "성에 제거 켜세요"),
                Triple(PopupTone.OK, "김서림 주의", "좋아요"),
                Triple(PopupTone.OK, "김서림 주의", "좋아요"),
            ),
            seen,
        )
        assertEquals(GuideKind.DEFOG, popup?.kind)
        // 김서림은 법규 근거가 없어 오늘 성적에 세지 않는다
        assertEquals(0 to 0, state.kept to state.total)
        assertEquals("안내 듣고 김서림을 잡았어요", state.message)
    }
}
