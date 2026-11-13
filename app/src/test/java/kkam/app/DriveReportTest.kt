package kkam.app

import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.LawEntry
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlin.test.Test
import kotlin.test.assertEquals

class DriveReportTest {
    private fun event(id: String, rule: RuleId, result: Result, guided: Boolean, turn: TurnType? = TurnType.LEFT) =
        Event(id, 11000, rule, result, guided, turn, 37.55, 127.07)

    private fun law(id: String, article: String, penalty: String) =
        LawEntry(id, "", "", article, "", penalty, "", disputed = false, note = "")

    private val laws = listOf(
        law("LAW-01", "제38조", "범칙금 3만 원"),
        law("LAW-12", "제5조", "범칙금 6만 원"),
    )

    @Test
    fun `안내 듣고 지킨 판정만 범칙금을 아낀 것으로 센다`() {
        val report = buildReport(
            listOf(
                event("evt-1", RuleId.R1, Result.KEPT, guided = true),
                event("evt-2", RuleId.R1, Result.KEPT, guided = false),
                event("evt-3", RuleId.R3, Result.KEPT, guided = true, turn = TurnType.RIGHT),
                event("evt-4", RuleId.R1, Result.MISSED, guided = true),
            ),
            laws,
        )

        assertEquals(3, report.kept)
        assertEquals(1, report.missed)
        assertEquals(90_000, report.savedWon) // R1 3만 원 + R3 6만 원
    }

    @Test
    fun `최근 판정이 먼저 오고 룰 테이블의 조항과 처분을 붙인다`() {
        val report = buildReport(
            listOf(
                event("evt-1", RuleId.R1, Result.KEPT, guided = true),
                event("evt-2", RuleId.R1, Result.MISSED, guided = false, turn = TurnType.RIGHT),
            ),
            laws,
        )

        assertEquals(
            listOf(
                ReportItem(false, "우회전 깜빡이 놓침", "놓쳤어요", "제38조", "처분: 범칙금 3만 원"),
                ReportItem(true, "좌회전 깜빡이 지킴", "안내 듣고 지켰어요", "제38조", "단속됐다면: 범칙금 3만 원"),
            ),
            report.items,
        )
    }

    @Test
    fun `룰 테이블에 없는 룰은 조항과 처분을 비운다`() {
        val report = buildReport(listOf(event("evt-1", RuleId.R2, Result.MISSED, guided = false)), laws)

        assertEquals(ReportItem(false, "깜빡이 없이 좌회전 놓침", "놓쳤어요", "", ""), report.items.single())
    }

    @Test
    fun `유턴 판정은 좌우회전 행이 아니라 유턴이 들어 있는 행의 조항을 붙인다`() {
        val report = buildReport(
            listOf(
                event("evt-1", RuleId.R1, Result.KEPT, guided = true, turn = TurnType.LEFT),
                event("evt-2", RuleId.R1, Result.MISSED, guided = true, turn = TurnType.UTURN),
            ),
            laws + law("LAW-03", "제38조제1항", "범칙금 3만 원 (유턴)"),
        )

        assertEquals(
            listOf(
                ReportItem(false, "유턴 깜빡이 놓침", "놓쳤어요", "제38조제1항", "처분: 범칙금 3만 원 (유턴)"),
                ReportItem(true, "좌회전 깜빡이 지킴", "안내 듣고 지켰어요", "제38조", "단속됐다면: 범칙금 3만 원"),
            ),
            report.items,
        )
    }

    @Test
    fun `회전교차로 판정은 진입·진출 조항을 따로 붙이고 범칙금 3만 원으로 센다`() {
        val report = buildReport(
            listOf(
                event("evt-1", RuleId.R8, Result.KEPT, guided = true, turn = TurnType.ROUNDABOUT_ENTER),
                event("evt-2", RuleId.R8, Result.MISSED, guided = true, turn = TurnType.ROUNDABOUT_EXIT),
            ),
            listOf(law("LAW-17", "별표 2 7호", "범칙금 3만 원 (진입)"), law("LAW-18", "별표 2 8호", "범칙금 3만 원 (진출)")),
        )

        assertEquals(
            listOf(
                ReportItem(false, "회전교차로 진출 깜빡이 놓침", "놓쳤어요", "별표 2 8호", "처분: 범칙금 3만 원 (진출)"),
                ReportItem(true, "회전교차로 진입 깜빡이 지킴", "안내 듣고 지켰어요", "별표 2 7호", "단속됐다면: 범칙금 3만 원 (진입)"),
            ),
            report.items,
        )
        assertEquals(30_000, report.savedWon)
    }

    @Test
    fun `나들목과 터널 판정도 리포트에 나온다`() {
        val report = buildReport(
            listOf(
                event("evt-1", RuleId.R4, Result.KEPT, guided = true, turn = TurnType.RIGHT),
                event("evt-2", RuleId.R5, Result.KEPT, guided = true, turn = null),
            ),
            laws + law("LAW-04", "제38조", "범칙금 3만 원"),
        )

        assertEquals(30_000, report.savedWon) // R4 3만 원. R5 처분 금액은 아직 확인하지 못해 0원
        assertEquals(
            listOf(
                ReportItem(true, "터널 전조등 지킴", "안내 듣고 지켰어요", "", ""),
                ReportItem(true, "오른쪽 나들목 깜빡이 지킴", "안내 듣고 지켰어요", "제38조", "단속됐다면: 범칙금 3만 원"),
            ),
            report.items,
        )
    }

    @Test
    fun `와이퍼 판정은 목록에는 나오지만 지킴률과 범칙금에는 세지 않는다`() {
        val report = buildReport(
            listOf(
                event("evt-1", RuleId.R1, Result.KEPT, guided = true),
                event("evt-2", RuleId.R6, Result.KEPT, guided = true, turn = null),
            ),
            laws,
        )

        assertEquals(1 to 0, report.kept to report.missed)
        assertEquals(30_000, report.savedWon)
        assertEquals(ReportItem(true, "비 올 때 와이퍼 지킴", "안내 듣고 지켰어요", "", ""), report.items.first())
    }

    @Test
    fun `금액은 만 원 단위로 읽기 쉽게 쓴다`() {
        assertEquals("0원", wonLabel(0))
        assertEquals("9만 원", wonLabel(90_000))
        assertEquals("15,000원", wonLabel(15_000))
    }

    @Test
    fun `새 주행을 시작해도 오늘 판정은 남는다`() {
        val frame = SignalFrame(11000, 15.0, Gear.D, 0.0, TurnSignal.LEFT, false, 37.55, 127.07, 30.0, null, null)
        val kept = event("evt-1", RuleId.R1, Result.KEPT, guided = true)

        val state = CoachState().resetDrive("S2").next(frame, listOf(kept)).resetDrive("S8")

        assertEquals(listOf(kept), state.todayEvents)
    }
}
