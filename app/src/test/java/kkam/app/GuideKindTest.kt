package kkam.app

import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `미리보기는 종류별 예시 값을 주황 카드와 같은 팝업으로 보여준다`() {
        val state = CoachState(kept = 1, total = 2).preview(GuideKind.HEADLIGHT)

        assertEquals(Tone.WARN, state.tone)
        assertEquals(GuideKind.HEADLIGHT, state.guideKind)
        assertEquals(1 to 2, state.kept to state.total)
        assertEquals(Popup(PopupTone.WARN, GuideKind.HEADLIGHT, false, "터널 진입", "200 m", "전조등 켜세요"), state.previewPopup())
    }
}
