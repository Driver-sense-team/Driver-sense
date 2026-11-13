package kkam.app

import kkam.engine.Engine
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.RuleId
import kkam.engine.model.Scenario
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuideKindTest {
    private val frame = SignalFrame(4000, 30.0, Gear.D, 0.0, TurnSignal.OFF, false, 37.55, 127.07, 90.0, Tbt(TurnType.LEFT, 60), null)

    @Test
    fun `음성 안내가 있는 룰만 안내 종류가 있다`() {
        assertEquals(GuideKind.TURN_SIGNAL, RuleId.R1.guideKind())
        assertEquals(GuideKind.RED_RIGHT_TURN, RuleId.R3.guideKind())
        assertEquals(GuideKind.LANE_CHANGE, RuleId.R4.guideKind())
        assertEquals(GuideKind.HEADLIGHT, RuleId.R5.guideKind())
        assertNull(RuleId.R2.guideKind())
    }

    @Test
    fun `음성 안내의 룰로 안내 종류를 정하고 팝업에도 넘긴다`() {
        // R3 안내는 적색 우회전에서만 나온다
        val right = frame.copy(tbt = Tbt(TurnType.RIGHT, 60))
        val guide = Guide(4000, RuleId.R3, "전방 적색이에요, 정지선에서 멈췄다 가세요", GuideAction.STOP)
        val state = CoachState().next(right, listOf(guide))

        assertEquals(GuideKind.RED_RIGHT_TURN, state.guideKind)
        assertEquals(GuideKind.RED_RIGHT_TURN, nextPopup(null, state, listOf(guide), right)?.kind)
    }

    @Test
    fun `새 주행은 회전 깜빡이 종류로 시작한다`() {
        val state = CoachState(guideKind = GuideKind.WIPER).resetDrive("S2")

        assertEquals(GuideKind.TURN_SIGNAL, state.guideKind)
    }

    @Test
    fun `유턴 시나리오(S25)는 유턴 카드로 왼쪽 깜빡이를 안내하고, 켜면 좋아요`() {
        val frames = Json { ignoreUnknownKeys = true }
            .decodeFromString(Scenario.serializer(), File("../scenarios/S25_유턴_안내후_켬.json").readText()).frames
        val engine = Engine()
        var state = CoachState()
        var popup: Popup? = null
        val popups = frames.map { f ->
            val out = engine.onFrame(f)
            state = state.next(f, out)
            popup = nextPopup(popup, state, out, f)
            popup
        }
        val warn = popups.filterNotNull().first()
        assertEquals(Popup(PopupTone.WARN, GuideKind.UTURN, false, "유턴", warn.distanceLabel, "왼쪽 깜빡이 켜세요", TurnType.UTURN), warn)
        assertEquals(PopupTone.OK, popups.filterNotNull().last().tone)
        assertEquals(LeverMotion.LEFT_DOWN, leverMotion(GuideKind.UTURN, right = false))
    }

    @Test
    fun `미리보기는 유턴도 보여준다`() {
        val state = CoachState().preview(GuideKind.UTURN)
        assertEquals(Popup(PopupTone.WARN, GuideKind.UTURN, false, "유턴", "60 m", "왼쪽 깜빡이 켜세요"), state.previewPopup())
        assertTrue(GuideKind.entries.indexOf(GuideKind.UTURN) == GuideKind.entries.indexOf(GuideKind.TURN_SIGNAL) + 1)
    }

    /** 시나리오 하나를 엔진 → 카드 상태 → 팝업 순서로 재생한 팝업들. */
    private fun popupsOf(scenario: String): List<Popup?> {
        val frames = Json { ignoreUnknownKeys = true }
            .decodeFromString(Scenario.serializer(), File("../scenarios/$scenario.json").readText()).frames
        val engine = Engine()
        var state = CoachState()
        var popup: Popup? = null
        return frames.map { f ->
            val out = engine.onFrame(f)
            state = state.next(f, out)
            popup = nextPopup(popup, state, out, f)
            popup
        }
    }

    @Test
    fun `회전교차로(S30)는 들어갈 때 왼쪽, 나갈 때 오른쪽 깜빡이 카드를 띄우고 켜면 좋아요`() {
        assertEquals(GuideKind.ROUNDABOUT, RuleId.R8.guideKind())
        val shown = popupsOf("S30_회전교차로_안내후_켬").filterNotNull()
        val enter = shown.first()
        assertEquals(Popup(PopupTone.WARN, GuideKind.ROUNDABOUT, false, "회전교차로 진입", enter.distanceLabel, "왼쪽 깜빡이 켜세요", TurnType.ROUNDABOUT_ENTER), enter)
        val exit = shown.first { it.forTurn == TurnType.ROUNDABOUT_EXIT && it.tone == PopupTone.WARN }
        assertEquals(Popup(PopupTone.WARN, GuideKind.ROUNDABOUT, true, "회전교차로 진출", exit.distanceLabel, "오른쪽 깜빡이 켜세요", TurnType.ROUNDABOUT_EXIT), exit)
        assertTrue(shown.any { it.forTurn == TurnType.ROUNDABOUT_EXIT && it.tone == PopupTone.OK })
        assertEquals(LeverMotion.LEFT_UP, leverMotion(GuideKind.ROUNDABOUT, right = true))
    }

    @Test
    fun `회전교차로(S32)에서 판정 지점을 꺼진 채 지나면 빨강`() {
        val shown = popupsOf("S32_회전교차로_깜빡이_안켬").filterNotNull()
        assertTrue(shown.any { it.forTurn == TurnType.ROUNDABOUT_ENTER && it.tone == PopupTone.MISS })
        assertTrue(shown.any { it.forTurn == TurnType.ROUNDABOUT_EXIT && it.tone == PopupTone.MISS })
    }

    @Test
    fun `미리보기에도 회전교차로가 있다 - 나갈 때 오른쪽 깜빡이`() {
        val state = CoachState().preview(GuideKind.ROUNDABOUT)
        assertEquals(Popup(PopupTone.WARN, GuideKind.ROUNDABOUT, true, "회전교차로 나갈 때", "40 m", "오른쪽 깜빡이 켜세요"), state.previewPopup())
    }

    @Test
    fun `미리보기는 종류별 예시 값을 주황 카드와 같은 팝업으로 보여준다`() {
        val state = CoachState(kept = 1, total = 2).preview(GuideKind.HEADLIGHT)

        assertEquals(Tone.WARN, state.tone)
        assertEquals(GuideKind.HEADLIGHT, state.guideKind)
        assertEquals(1 to 2, state.kept to state.total)
        assertEquals(Popup(PopupTone.WARN, GuideKind.HEADLIGHT, false, "터널 진입", "200 m", "전조등 켜세요"), state.previewPopup())
    }
}
