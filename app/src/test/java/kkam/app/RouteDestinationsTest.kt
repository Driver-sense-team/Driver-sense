package kkam.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteDestinationsTest {
    private val pangyo = DEMO_DESTINATIONS.single()

    @Test
    fun `시연 입력 "현대자동차 판교"와 입력 중인 앞부분으로 찾는다`() {
        assertEquals(listOf(pangyo), searchDestinations("현대자동차 판교"))
        assertEquals(listOf(pangyo), searchDestinations("현대"))
        assertEquals(listOf(pangyo), searchDestinations("현대자동차판교"))
        assertEquals(listOf(pangyo), searchDestinations("포티투닷"))
        assertEquals(listOf(pangyo), searchDestinations("42dot"))
    }

    @Test
    fun `빈 입력과 없는 곳은 결과가 없다`() {
        assertEquals(emptyList(), searchDestinations("  "))
        assertEquals(emptyList(), searchDestinations("부산역"))
    }

    @Test
    fun `차선 변경 팝업은 각본 시각에만 뜨고 깜빡이를 켜면 파랑이 된다`() {
        val cue = RouteDemo.LaneCue(showAt = 10_000, okAt = 12_000, hideAt = 17_000)
        assertEquals(null, laneChangePopup(listOf(cue), 9_000))
        assertEquals(PopupTone.WARN, laneChangePopup(listOf(cue), 10_000)?.tone)
        assertEquals(GuideKind.LANE_CHANGE, laneChangePopup(listOf(cue), 11_000)?.kind)
        assertEquals("좋아요", laneChangePopup(listOf(cue), 12_000)?.headline)
        assertEquals(null, laneChangePopup(listOf(cue), 17_000))
    }

    @Test
    fun `경로 리포트의 AI에게 묻기 질문은 룰 테이블에 근거가 있는 추천 질문이다`() {
        assertTrue(ROUTE_REPORT_QUESTION in SUGGESTED_QUESTIONS)
        assertTrue("우회전" in ROUTE_REPORT_QUESTION)
    }

    @Test
    fun `출발할 때 내비 띄우기 - 경로 데모 중에는 안 띄우고, 실제 주행과 시나리오 재생은 그대로 띄운다`() {
        assertEquals(false, opensNaviOnDeparture(CoachSession.ROUTE_DEMO))
        assertEquals(true, opensNaviOnDeparture(CoachSession.LIVE))
        assertEquals(true, opensNaviOnDeparture("S2_우회전_안내후켬.json"))
        assertEquals(true, opensNaviOnDeparture(null))
    }
}
