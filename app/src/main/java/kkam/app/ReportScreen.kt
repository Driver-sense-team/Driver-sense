package kkam.app

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kkam.engine.model.Event
import kkam.engine.model.LawEntry
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 주행 리포트 (기획서 UI-2, screen_report.xml). 기어 P일 때만 보인다.
 * 값은 [buildReport]에서만 온다. 색은 다른 화면과 같은 공통 토큰을 쓴다.
 * 일·월·연도 단위로 나눠 본다. 기간 계산과 제목·라벨 문구는 ReportPeriod.kt의 순수 함수가 정한다.
 *
 * 화면 구성 (읽는 순서)
 * 0. 기간 전환: 일 / 월 / 연도 칩, 이전·다음 기간 화살표와 기간 이름 (예: "‹ 2026년 11월 ›")
 * 1. 제목(예: "이번 달 주행 리포트") + 핵심 요약 한 줄
 * 2. 요약 수치 카드 3개: 지킴, 놓침, 아낀 범칙금
 * 3. 판정 기록 리스트: 시간 | 상황 | 결과 칩, 아래 조항·처분. 비었으면 아이콘 + 안내 문구
 */
class ReportScreen(
    private val activity: Activity,
    /** 날짜별 판정 기록. 세션과 같은 저장소(같은 SharedPreferences)라 따로 넘겨받지 않는다. */
    private val records: RecordStore = RecordStore(activity),
    /** 법규 룰 테이블. 리포트를 처음 그릴 때 읽는다. */
    private val laws: () -> List<LawEntry>,
) {
    private val root: View = activity.findViewById(R.id.reportScreen)
    private val summary: TextView = activity.findViewById(R.id.reportSummary)
    private val statKept: View = activity.findViewById(R.id.reportStatKept)
    private val statMissed: View = activity.findViewById(R.id.reportStatMissed)
    private val statSaved: View = activity.findViewById(R.id.reportStatSaved)
    private val items: LinearLayout = activity.findViewById(R.id.reportItems)
    private val empty: View = activity.findViewById(R.id.reportEmpty)
    private val emptyTitle: TextView = activity.findViewById(R.id.reportEmptyTitle)
    private val title: TextView = activity.findViewById(R.id.reportTitle)
    private val range: TextView = activity.findViewById(R.id.reportRange)
    private val prev: TextView = activity.findViewById(R.id.reportPrev)
    private val next: TextView = activity.findViewById(R.id.reportNext)
    private val periodTabs: Map<ReportPeriod, TextView> = mapOf(
        ReportPeriod.DAY to activity.findViewById<TextView>(R.id.reportPeriodDay),
        ReportPeriod.MONTH to activity.findViewById<TextView>(R.id.reportPeriodMonth),
        ReportPeriod.YEAR to activity.findViewById<TextView>(R.id.reportPeriodYear),
    )

    /** 고른 단위. 화면을 다시 열어도 유지한다. */
    private var period = ReportPeriod.DAY
    /** 보고 있는 기간 안의 아무 날짜. 화면을 열 때마다 오늘로 돌아온다. */
    private var anchor: LocalDate = records.today()
    private var visible = false

    /** 세션의 오늘 판정 목록. 새 판정이 나오면 바뀌므로 다시 그릴지 정하는 데 쓴다. */
    private var todayEvents: List<Event> = emptyList()
    /** 리포트를 마지막으로 그린 조건. 같으면 다시 그리지 않는다. */
    private var drawnFor: DrawKey? = null

    private data class DrawKey(val period: ReportPeriod, val anchor: LocalDate, val today: LocalDate, val todayEvents: List<Event>)

    init {
        listOf(statKept, statMissed, statSaved).forEach { it.background = activity.cardBackground() }
        activity.findViewById<View>(R.id.reportListCard).background = activity.cardBackground()
        activity.findViewById<View>(R.id.reportEmptyIcon).background = activity.pillBackground(color(R.color.surface_2))
        activity.findViewById<View>(R.id.reportAsk).background =
            activity.pillBackground(color(R.color.surface), strokeColor = color(R.color.line))

        // 수치 카드의 고정 부분: 기호, 라벨, 숫자 색
        statKept.setStatHead("✓", R.string.report_kept, R.color.ok, R.color.ok_soft)
        statMissed.setStatHead("!", R.string.report_missed, R.color.miss, R.color.miss_soft)
        statSaved.setStatHead("₩", R.string.report_saved, R.color.text, R.color.surface_2)

        // 0. 기간 전환: 칩 묶음은 흰 알약, 고른 칩은 옅은 파랑. 화살표는 흰 동그라미.
        activity.findViewById<View>(R.id.reportPeriodTabs).background =
            activity.pillBackground(color(R.color.surface), strokeColor = color(R.color.line))
        periodTabs.forEach { (target, tab) -> tab.setOnClickListener { selectPeriod(target) } }
        listOf(prev, next).forEach {
            it.background = activity.pillBackground(color(R.color.surface), strokeColor = color(R.color.line))
            it.setTextColor(color(R.color.text))
        }
        prev.setOnClickListener { moveBy(-1) }
        next.setOnClickListener { moveBy(1) }
    }

    fun show(screen: Screen) {
        val nowVisible = screen == Screen.REPORT
        // 화면을 새로 열면 오늘이 든 기간부터 보여준다 (고른 단위는 그대로)
        if (nowVisible && !visible) anchor = records.today()
        visible = nowVisible
        root.visibility = if (nowVisible) View.VISIBLE else View.GONE
    }

    /** 판정 목록이나 고른 기간이 바뀌었을 때만 다시 그린다. */
    fun render(state: CoachState) {
        todayEvents = state.todayEvents
        redraw()
    }

    /** 일·월·연도 칩. 단위를 바꾸면 오늘이 든 기간으로 돌아간다 (10월을 보다가 '일'을 누르면 10월 12일이 아니라 오늘). */
    private fun selectPeriod(target: ReportPeriod) {
        if (target == period) return
        period = target
        anchor = records.today()
        redraw()
    }

    /** 이전(-1)·다음(+1) 기간. 화살표가 막혀 있으면 아무것도 하지 않는다. */
    private fun moveBy(steps: Long) {
        val today = records.today()
        if (steps > 0 && !period.canGoNext(anchor, today)) return
        anchor = period.shift(anchor, steps)
        redraw()
    }

    private fun redraw() {
        val today = records.today()
        val key = DrawKey(period, anchor, today, todayEvents)
        if (drawnFor == key) return
        drawnFor = key

        // 판정은 세션이 나올 때마다 저장소에 쓰므로(CoachSession) 저장소가 오늘 기록까지 갖고 있다.
        val history = records.history()
        val dated = history.eventsIn(period.spanOf(anchor))

        drawPeriodBar(history, today)
        title.text = period.reportTitle(anchor, today)
        statSaved.findViewById<TextView>(R.id.statLabel).text = period.savedLabel(anchor, today)
        emptyTitle.text = period.emptyLabel(anchor, today)
        // report.items는 최근 판정이 먼저라 시간도 같은 순서로 맞춘다
        draw(
            buildReport(dated.map { it.event }, laws()),
            times = dated.reversed().map { period.itemTimeLabel(it) },
            current = period.isCurrent(anchor, today),
        )
    }

    /** 칩 색과 기간 이름, 화살표를 누를 수 있는지. */
    private fun drawPeriodBar(history: DailyRecords, today: LocalDate) {
        periodTabs.forEach { (target, tab) ->
            val on = target == period
            tab.background = if (on) activity.pillBackground(color(R.color.ok_soft)) else null
            tab.setTextColor(color(if (on) R.color.ok else R.color.muted))
            tab.isSelected = on
        }
        range.text = period.rangeLabel(anchor, today)
        prev.setUsable(period.canGoPrev(anchor, history))
        next.setUsable(period.canGoNext(anchor, today))
    }

    /** 누를 수 없는 화살표는 흐리게 한다. */
    private fun View.setUsable(usable: Boolean) {
        isEnabled = usable
        alpha = if (usable) 1f else DISABLED_ALPHA
    }

    private fun draw(report: DriveReport, times: List<String>, current: Boolean) {
        val total = report.kept + report.missed
        // "0원"도 다른 카드처럼 숫자 크게, 단위 작게 보이게 띄어 쓴다
        val savedLabel = if (report.savedWon == 0) ZERO_WON else wonLabel(report.savedWon)

        // 1. 핵심 요약 한 줄. 지난 기간이 비었으면 "출발하면" 안내 대신 기록이 없다고만 말한다.
        summary.text = when {
            total == 0 && !current -> activity.getString(R.string.report_summary_empty_past)
            total == 0 -> activity.getString(R.string.parked_report_summary_empty)
            report.savedWon > 0 -> activity.getString(R.string.parked_report_summary_saved, total, report.kept, savedLabel)
            else -> activity.getString(R.string.parked_report_summary, total, report.kept)
        }

        // 2. 수치 카드 3개
        statKept.setStatBody(
            value = countLabel(report.kept),
            caption = if (total == 0) {
                activity.getString(R.string.parked_report_none_caption)
            } else {
                activity.getString(R.string.parked_report_kept_caption, (report.kept * 100.0 / total).roundToInt())
            },
        )
        statMissed.setStatBody(
            value = countLabel(report.missed),
            caption = activity.getString(
                if (report.missed == 0) R.string.parked_report_missed_none else R.string.parked_report_missed_caption,
            ),
        )
        // 놓침 0번은 나쁜 소식이 아니므로 빨강 대신 회색 숫자로
        statMissed.findViewById<TextView>(R.id.statValue)
            .setTextColor(color(if (report.missed == 0) R.color.muted else R.color.miss))
        statSaved.setStatBody(value = savedLabel, caption = activity.getString(R.string.parked_report_saved_caption))

        // 3. 판정 기록
        empty.visibility = if (report.items.isEmpty()) View.VISIBLE else View.GONE
        items.removeAllViews()
        report.items.forEachIndexed { index, item ->
            if (index > 0) items.addView(divider())
            addItem(item, times.getOrNull(index))
        }
    }

    /** 수치 카드 위쪽: 기호 동그라미, 라벨, 숫자 색. */
    private fun View.setStatHead(symbol: String, labelRes: Int, toneRes: Int, softRes: Int) {
        findViewById<TextView>(R.id.statIcon).apply {
            text = symbol
            setTextColor(color(toneRes))
            background = activity.pillBackground(color(softRes))
        }
        findViewById<TextView>(R.id.statLabel).setText(labelRes)
        findViewById<TextView>(R.id.statValue).setTextColor(color(toneRes))
    }

    /** 수치 카드 아래쪽: "21만 원"(숫자 크게, "만 원"은 작게)과 보조 설명. */
    private fun View.setStatBody(value: String, caption: String) {
        findViewById<TextView>(R.id.statValue).text = digitsEmphasis(value)
        findViewById<TextView>(R.id.statCaption).text = caption
    }

    /** 판정 한 줄: 시간 | 상황 + 설명 | 결과 칩, 아래 조항·처분. */
    private fun addItem(item: ReportItem, time: String?) {
        val row = activity.layoutInflater.inflate(R.layout.report_item, items, false)
        val tone = color(if (item.kept) R.color.ok else R.color.miss)

        row.findViewById<TextView>(R.id.itemTime).text = time.orEmpty()
        // 결과는 오른쪽 칩이 말하므로 제목에서는 "지킴"/"놓침"을 뗀다. 예: "좌회전 깜빡이 지킴" → "좌회전 깜빡이"
        row.findViewById<TextView>(R.id.itemTitle).text = item.title.substringBeforeLast(" ")
        row.findViewById<TextView>(R.id.itemDetail).text = item.detail
        row.findViewById<TextView>(R.id.itemResult).apply {
            setText(if (item.kept) R.string.parked_report_kept_chip else R.string.parked_report_missed_chip)
            setTextColor(tone)
            background = activity.pillBackground(color(if (item.kept) R.color.ok_soft else R.color.miss_soft))
        }
        row.findViewById<TextView>(R.id.itemArticle).showOrHide(item.article)
        row.findViewById<TextView>(R.id.itemPenalty).apply {
            showOrHide(item.penalty)
            setTextColor(if (item.kept) color(R.color.muted) else tone)
        }
        items.addView(row)
    }

    /** 판정 줄 사이 구분선. */
    private fun divider() = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(1))
        setBackgroundColor(color(R.color.line))
    }

    /** 글자가 비었으면(룰 테이블에 없음) 줄을 숨긴다. */
    private fun TextView.showOrHide(label: String) {
        text = label
        visibility = if (label.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun countLabel(count: Int) = activity.getString(R.string.parked_report_count, count)

    private fun color(id: Int) = activity.getColor(id)

    private companion object {
        const val ZERO_WON = "0 원"
        /** 누를 수 없는 화살표의 투명도. */
        const val DISABLED_ALPHA = 0.35f
    }
}
