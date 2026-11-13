package kkam.app

import kkam.engine.model.Event
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.TurnType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportPeriodTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun event(id: String, result: Result, guided: Boolean = true, t: Long = 11_000) =
        Event(id, t, RuleId.R1, result, guided, TurnType.LEFT, 37.55, 127.07)

    /** 오늘 2건, 같은 달 다른 날 1건, 지난달 1건, 작년 1건. */
    private val records: DailyRecords = mapOf(
        today to listOf(event("today-1", Result.KEPT), event("today-2", Result.MISSED)),
        LocalDate.of(2026, 11, 3) to listOf(event("nov-3", Result.KEPT)),
        LocalDate.of(2026, 10, 20) to listOf(event("oct-20", Result.KEPT)),
        LocalDate.of(2025, 5, 1) to listOf(event("2025", Result.KEPT)),
    )

    private fun ids(period: ReportPeriod, anchor: LocalDate = today) =
        records.eventsIn(period.spanOf(anchor)).map { it.event.id }

    @Test
    fun `기간은 그날, 그달 1일부터 말일, 그해 1월 1일부터 12월 31일이다`() {
        assertEquals(DateSpan(today, LocalDate.of(2026, 11, 13)), ReportPeriod.DAY.spanOf(today))
        assertEquals(DateSpan(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)), ReportPeriod.MONTH.spanOf(today))
        assertEquals(DateSpan(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)), ReportPeriod.YEAR.spanOf(today))
        // 끝 날짜는 들어가지 않는다
        assertFalse(LocalDate.of(2026, 12, 1) in ReportPeriod.MONTH.spanOf(today))
    }

    @Test
    fun `일은 오늘 판정만, 월은 이번 달, 연도는 올해 판정만 모은다`() {
        assertEquals(listOf("today-1", "today-2"), ids(ReportPeriod.DAY))
        // 오래된 날짜가 먼저, 같은 날은 나온 순서
        assertEquals(listOf("nov-3", "today-1", "today-2"), ids(ReportPeriod.MONTH))
        assertEquals(listOf("oct-20", "nov-3", "today-1", "today-2"), ids(ReportPeriod.YEAR))
        assertEquals(listOf("2025"), ids(ReportPeriod.YEAR, LocalDate.of(2025, 1, 1)))
    }

    @Test
    fun `아낀 범칙금은 고른 기간 안의 판정만 센다`() {
        fun saved(period: ReportPeriod) = buildReport(records.eventsIn(period.spanOf(today)).map { it.event }, emptyList()).savedWon

        assertEquals(30_000, saved(ReportPeriod.DAY))
        assertEquals(60_000, saved(ReportPeriod.MONTH))
        assertEquals(90_000, saved(ReportPeriod.YEAR))
    }

    @Test
    fun `이전 다음으로 넘기면 같은 단위만큼 옮긴다`() {
        assertEquals(LocalDate.of(2026, 11, 11), ReportPeriod.DAY.shift(today, -1))
        assertEquals(LocalDate.of(2026, 10, 12), ReportPeriod.MONTH.shift(today, -1))
        assertEquals(LocalDate.of(2025, 11, 12), ReportPeriod.YEAR.shift(today, -1))
        // 1월 31일에서 한 달 앞이면 2월 말일 (그래도 같은 2월이다)
        assertEquals(LocalDate.of(2026, 2, 28), ReportPeriod.MONTH.shift(LocalDate.of(2026, 1, 31), 1))
    }

    @Test
    fun `오늘이 든 기간이면 다음으로 넘길 수 없다`() {
        assertFalse(ReportPeriod.DAY.canGoNext(today, today))
        assertFalse(ReportPeriod.MONTH.canGoNext(LocalDate.of(2026, 11, 1), today))
        assertFalse(ReportPeriod.YEAR.canGoNext(today, today))
        assertTrue(ReportPeriod.DAY.canGoNext(today.minusDays(1), today))
        assertTrue(ReportPeriod.MONTH.canGoNext(LocalDate.of(2026, 10, 31), today))
    }

    @Test
    fun `이전 기간은 그보다 앞선 판정이 있을 때만 넘길 수 있다`() {
        assertTrue(ReportPeriod.MONTH.canGoPrev(today, records))
        assertFalse(ReportPeriod.YEAR.canGoPrev(LocalDate.of(2025, 5, 1), records))
        assertFalse(ReportPeriod.DAY.canGoPrev(today, emptyMap()))
    }

    @Test
    fun `제목은 단위와 기간에 맞게 바뀐다`() {
        assertEquals("오늘 주행 리포트", ReportPeriod.DAY.reportTitle(today, today))
        assertEquals("어제 주행 리포트", ReportPeriod.DAY.reportTitle(today.minusDays(1), today))
        assertEquals("11월 3일 주행 리포트", ReportPeriod.DAY.reportTitle(LocalDate.of(2026, 11, 3), today))
        assertEquals("2025년 5월 1일 주행 리포트", ReportPeriod.DAY.reportTitle(LocalDate.of(2025, 5, 1), today))
        assertEquals("이번 달 주행 리포트", ReportPeriod.MONTH.reportTitle(LocalDate.of(2026, 11, 1), today))
        assertEquals("지난달 주행 리포트", ReportPeriod.MONTH.reportTitle(LocalDate.of(2026, 10, 12), today))
        assertEquals("9월 주행 리포트", ReportPeriod.MONTH.reportTitle(LocalDate.of(2026, 9, 12), today))
        assertEquals("올해 주행 리포트", ReportPeriod.YEAR.reportTitle(today, today))
        assertEquals("작년 주행 리포트", ReportPeriod.YEAR.reportTitle(LocalDate.of(2025, 1, 1), today))
        assertEquals("2024년 주행 리포트", ReportPeriod.YEAR.reportTitle(LocalDate.of(2024, 1, 1), today))
    }

    @Test
    fun `1월에 보면 지난달은 작년 12월이다`() {
        val january = LocalDate.of(2027, 1, 5)

        assertEquals("지난달", ReportPeriod.MONTH.periodName(LocalDate.of(2026, 12, 1), january))
        assertEquals("2026년 11월", ReportPeriod.MONTH.periodName(LocalDate.of(2026, 11, 1), january))
    }

    @Test
    fun `아낀 범칙금 라벨은 기간 이름을 붙인다`() {
        assertEquals("오늘 아낀 범칙금", ReportPeriod.DAY.savedLabel(today, today))
        assertEquals("이번 달에 아낀 범칙금", ReportPeriod.MONTH.savedLabel(today, today))
        assertEquals("올해 아낀 범칙금", ReportPeriod.YEAR.savedLabel(today, today))
        assertEquals("9월에 아낀 범칙금", ReportPeriod.MONTH.savedLabel(LocalDate.of(2026, 9, 1), today))
        assertEquals("11월 3일에 아낀 범칙금", ReportPeriod.DAY.savedLabel(LocalDate.of(2026, 11, 3), today))
    }

    @Test
    fun `기간 이름 칸은 절대 날짜를 보여준다`() {
        assertEquals("11월 12일", ReportPeriod.DAY.rangeLabel(today, today))
        assertEquals("2026년 11월", ReportPeriod.MONTH.rangeLabel(today, today))
        assertEquals("2026년", ReportPeriod.YEAR.rangeLabel(today, today))
    }

    @Test
    fun `빈 상태 문구는 지금 기간이면 아직이라고 말한다`() {
        assertEquals("오늘은 아직 판정이 없어요", ReportPeriod.DAY.emptyLabel(today, today))
        assertEquals("이날은 판정이 없어요", ReportPeriod.DAY.emptyLabel(today.minusDays(1), today))
        assertEquals("이번 달은 아직 판정이 없어요", ReportPeriod.MONTH.emptyLabel(today, today))
        assertEquals("올해는 아직 판정이 없어요", ReportPeriod.YEAR.emptyLabel(today, today))
    }

    @Test
    fun `일 리포트는 시간만, 월과 연도 리포트는 날짜를 윗줄에 붙인다`() {
        val item = DatedEvent(LocalDate.of(2026, 11, 3), event("e", Result.KEPT, t = 42_000))

        assertEquals("42초", ReportPeriod.DAY.itemTimeLabel(item))
        assertEquals("11/3\n42초", ReportPeriod.MONTH.itemTimeLabel(item))
        assertEquals("11/3\n42초", ReportPeriod.YEAR.itemTimeLabel(item))
    }
}
