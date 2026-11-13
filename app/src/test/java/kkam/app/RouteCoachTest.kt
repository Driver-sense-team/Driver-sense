package kkam.app

import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.TurnType
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteCoachTest {
    private fun event(id: String, result: Result, turn: TurnType = TurnType.LEFT, rule: RuleId = RuleId.R1) =
        Event(id, 11000, rule, result, guided = true, turn = turn, lat = 37.55, lon = 127.07)

    private val plan = RoutePlan(
        destination = "판교역",
        distanceM = 4200,
        turns = listOf(TurnType.LEFT, TurnType.STRAIGHT, TurnType.RIGHT, TurnType.LEFT, TurnType.RAMP_RIGHT, TurnType.UTURN),
    )

    @Test
    fun `미리보기는 내비처럼 거리와 예상 시간을 주고, 코치할 지점만 센다`() {
        val preview = buildRoutePreview(plan, emptyList(), diagnosis = null)

        assertEquals(5, preview.coachPoints) // 좌·우·좌회전, 나들목, 유턴. 직진은 코치하지 않는다
        assertEquals("4.2 km", preview.distanceLabel)
        // 소요 시간을 모르면 거리 ÷ 도심 평균 30 km/h, 올림: 4.2 km → 8.4분 → 9분
        assertEquals(9, preview.minutes)
        assertNull(preview.weakSpot)
        // 내비·데모가 준 소요 시간이 있으면 그 값
        assertEquals(35, buildRoutePreview(plan.copy(durationMin = 35), emptyList(), null).minutes)
        assertNull(buildRoutePreview(plan.copy(distanceM = 0), emptyList(), null).minutes)
    }

    @Test
    fun `소요 시간과 도착 시각 표시`() {
        assertEquals("35분", durationLabel(35))
        assertEquals("1시간", durationLabel(60))
        assertEquals("1시간 35분", durationLabel(95))
        assertEquals("오후 1:35 도착", arrivalLabel(LocalTime.of(13, 0), 35))
        assertEquals("오전 12:05 도착", arrivalLabel(LocalTime.of(23, 50), 15))
        assertEquals("오후 12:00 도착", arrivalLabel(LocalTime.of(11, 30), 30))
        assertEquals("오전 9:07 도착", arrivalLabel(LocalTime.of(8, 59), 8))
    }

    @Test
    fun `오늘 놓친 판정이 있으면 가장 많이 놓친 종류를 약한 곳으로 고른다`() {
        val today = listOf(
            event("e1", Result.MISSED, TurnType.RIGHT),
            event("e2", Result.MISSED, TurnType.LEFT),
            event("e3", Result.MISSED, TurnType.LEFT),
            event("e4", Result.KEPT, TurnType.RIGHT),
        )
        val preview = buildRoutePreview(plan, today, DemoDiagnosisSource.load())

        assertEquals(WeakSpot("좌회전 깜빡이", setOf(TurnType.LEFT), fromToday = true), preview.weakSpot)
        assertEquals(2, preview.weakOnRoute)
    }

    @Test
    fun `놓친 개수가 같으면 최근에 놓친 종류를 고른다`() {
        val today = listOf(event("e1", Result.MISSED, TurnType.LEFT), event("e2", Result.MISSED, TurnType.RIGHT))

        assertEquals("우회전 깜빡이", weakSpotOf(today, null)?.label)
    }

    @Test
    fun `오늘 놓친 게 없으면 진단에서 준수율이 가장 낮은 습관을 쓴다`() {
        val spot = weakSpotOf(listOf(event("e1", Result.KEPT)), DemoDiagnosisSource.load())

        // 데모 진단: 좌회전 62%, 우회전 85%, 적색 우회전 78%
        assertEquals(WeakSpot("좌회전 깜빡이", setOf(TurnType.LEFT), fromToday = false), spot)
    }

    @Test
    fun `적색 우회전과 나들목 놓침은 우회전과 나들목에서 센다`() {
        val red = weakSpotOf(listOf(event("e1", Result.MISSED, rule = RuleId.R3)), null)
        val ramp = weakSpotOf(listOf(event("e1", Result.MISSED, TurnType.RIGHT, RuleId.R4)), null)

        assertEquals(setOf(TurnType.RIGHT), red?.turns)
        assertEquals(1, buildRoutePreview(plan, listOf(event("e1", Result.MISSED, TurnType.RIGHT, RuleId.R4)), null).weakOnRoute)
        assertEquals(setOf(TurnType.RAMP_LEFT, TurnType.RAMP_RIGHT), ramp?.turns)
    }

    @Test
    fun `거리 표시`() {
        assertEquals("", distanceLabel(0))
        assertEquals("800 m", distanceLabel(800))
        assertEquals("1.2 km", distanceLabel(1234))
        assertEquals("12.0 km", distanceLabel(12_000))
    }

    @Test
    fun `경로 시작 문장은 목적지와 코치할 지점을 말한다`() {
        assertEquals("판교역까지 안내를 시작해요. 좌회전 2곳, 우회전 1곳, 나들목 1곳, 유턴 1곳에서 챙길게요.", routeStartMessage(plan))
        assertEquals("집까지 안내를 시작해요.", routeStartMessage(RoutePlan("집", 0, listOf(TurnType.STRAIGHT))))
    }

    @Test
    fun `경로 시작 전이표 - 코치 끔이면 무시, 켬이면 멈춤은 켜고 실차는 붙이고 재생 중은 무시`() {
        assertEquals(RouteStartAction.START_LIVE, routeStartAction(null, coachOn = true))
        assertEquals(RouteStartAction.ATTACH, routeStartAction(CoachSession.LIVE, coachOn = true))
        assertEquals(RouteStartAction.IGNORE, routeStartAction("S2", coachOn = true))
        assertEquals(RouteStartAction.IGNORE, routeStartAction(CoachSession.ROUTE_DEMO, coachOn = true))
        assertEquals(RouteStartAction.IGNORE, routeStartAction(null, coachOn = false))
        assertEquals(RouteStartAction.IGNORE, routeStartAction(CoachSession.LIVE, coachOn = false))
    }

    @Test
    fun `경로 판정은 경로를 붙인 뒤에 나온 판정만 센다`() {
        val before = CoachState(todayEvents = listOf(event("old", Result.MISSED)))
        val started = before.startRoute(plan)
        val driven = started.copy(todayEvents = started.todayEvents + event("e1", Result.KEPT) + event("e2", Result.MISSED))

        val trip = driven.route!!
        assertEquals(listOf("e1", "e2"), trip.events(driven.todayEvents).map { it.id })
        assertEquals("경로 주행 중 · 2번 중 1번 지켰어요", routeSummary(trip, driven.todayEvents))
    }

    @Test
    fun `경로 리포트 머리 문구는 와이퍼·김서림을 빼고 센다 - 지킨 곳 카드와 같은 기준`() {
        val started = CoachState().startRoute(plan)
        val events = listOf(
            event("e1", Result.KEPT),
            event("e2", Result.MISSED),
            event("rain", Result.KEPT, rule = RuleId.R6),
            event("fog", Result.KEPT, rule = RuleId.R7),
        )
        val driven = started.copy(todayEvents = events)

        assertEquals("경로 주행 중 · 2번 중 1번 지켰어요", routeSummary(driven.route!!, driven.todayEvents))
        // 와이퍼·김서림만 있으면 셀 판정이 없다
        val rainOnly = started.copy(todayEvents = listOf(event("rain", Result.KEPT, rule = RuleId.R6)))
        assertEquals("경로 주행 중 · 아직 판정이 없어요", routeSummary(rainOnly.route!!, rainOnly.todayEvents))
    }

    @Test
    fun `도착하면 한 번만 끝내고 요약 문구를 보여준다`() {
        val started = CoachState().startRoute(plan)
        assertEquals("판교역까지 안내를 시작해요. 좌회전 2곳, 우회전 1곳, 나들목 1곳, 유턴 1곳에서 챙길게요.", started.message)

        val arrived = started.copy(todayEvents = listOf(event("e1", Result.KEPT))).endRoute(RouteStatus.ARRIVED)
        assertEquals(RouteStatus.ARRIVED, arrived.route?.status)
        assertEquals("도착했어요 · 1번 중 1번 지켰어요", arrived.message)

        // 도착 뒤에 안내 종료가 또 와도 도착으로 남는다
        assertEquals(RouteStatus.ARRIVED, arrived.endRoute(RouteStatus.ENDED).route?.status)
        // 경로가 없으면 아무것도 바꾸지 않는다
        assertEquals(CoachState(), CoachState().endRoute(RouteStatus.ARRIVED))
    }

    @Test
    fun `새 주행을 시작하면 경로를 비운다`() {
        assertNull(CoachState().startRoute(plan).resetDrive("S2").route)
    }

    @Test
    fun `경로가 시작됐을 때 기어 P면 미리보기, 주행 중이면 그대로, 기어를 모르면 기다린다`() {
        assertEquals(Screen.ROUTE_PREVIEW, screenOnRouteStart(Screen.REPORT, Gear.P))
        assertEquals(Screen.DRIVE, screenOnRouteStart(Screen.DRIVE, Gear.D))
        assertNull(screenOnRouteStart(Screen.DRIVE, null))
        // 미리보기는 정차 화면이라 기어 P면 리포트로 덮지 않고, D면 주행 화면으로 간다
        assertEquals(Screen.ROUTE_PREVIEW, nextScreen(Screen.ROUTE_PREVIEW, Gear.P))
        assertEquals(Screen.DRIVE, nextScreen(Screen.ROUTE_PREVIEW, Gear.D))
    }

    @Test
    fun `경로가 끝난 뒤 기어 P가 되면 경로 리포트, 경로 주행 중이거나 경로가 없으면 오늘 리포트`() {
        val driving = RouteTrip(plan, firstEventIndex = 0)
        val arrived = driving.copy(status = RouteStatus.ARRIVED)
        val ended = driving.copy(status = RouteStatus.ENDED)

        assertEquals(Screen.ROUTE_REPORT, nextScreen(Screen.DRIVE, Gear.P, arrived))
        assertEquals(Screen.ROUTE_REPORT, nextScreen(Screen.DRIVE, Gear.P, ended))
        assertEquals(Screen.REPORT, nextScreen(Screen.DRIVE, Gear.P, driving))
        assertEquals(Screen.REPORT, nextScreen(Screen.DRIVE, Gear.P, null))
        // 정차 화면을 보고 있으면 덮지 않고, D면 주행 화면
        assertEquals(Screen.ROUTE_REPORT, nextScreen(Screen.ROUTE_REPORT, Gear.P, arrived))
        assertEquals(Screen.QNA, nextScreen(Screen.QNA, Gear.P, arrived))
        assertEquals(Screen.DRIVE, nextScreen(Screen.ROUTE_REPORT, Gear.D, arrived))
    }

    @Test
    fun `경로 리포트는 놓친 곳을 먼저 보여준다`() {
        val today = listOf(event("old", Result.MISSED), event("e1", Result.KEPT), event("e2", Result.MISSED), event("e3", Result.KEPT))
        val trip = RouteTrip(plan, firstEventIndex = 1, status = RouteStatus.ARRIVED)

        val items = routeReportItems(trip, today)
        assertEquals(listOf(false, true, true), items.map { it.kept })
        assertEquals(3, items.size) // 경로 전 판정(old)은 빠진다
    }
}
