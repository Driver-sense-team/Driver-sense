package kkam.app

import android.app.Activity
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kkam.engine.model.LawEntry
import kkam.engine.model.Result

/**
 * 경로 리포트 (screen_route_report.xml). 경로가 끝나고 기어 P가 되면 보인다.
 * 이번 경로의 판정만 묶고, 잘못한 점(놓친 곳)을 먼저 보여준다. 카드 값은 [routeReportItems]에서 온다.
 * 오늘 리포트([ReportScreen])와 화면·행 레이아웃 파일을 나눠 서로 디자인을 바꿔도 겹치지 않게 했다.
 */
class RouteReportScreen(
    private val activity: Activity,
    private val laws: () -> List<LawEntry>,
    /** "AI에게 묻기"를 눌렀을 때. MainActivity가 법규 Q&A를 열고 [ROUTE_REPORT_QUESTION]을 묻는다. */
    private val onAsk: () -> Unit,
) {
    private val root: View = activity.findViewById(R.id.routeReportScreen)
    private val title: TextView = activity.findViewById(R.id.routeReportTitle)
    private val summary: TextView = activity.findViewById(R.id.routeReportSummary)
    private val missed: TextView = activity.findViewById(R.id.routeReportMissed)
    private val missedHint: TextView = activity.findViewById(R.id.routeReportMissedHint)
    private val kept: TextView = activity.findViewById(R.id.routeReportKept)
    private val saved: TextView = activity.findViewById(R.id.routeReportSaved)
    private val items: LinearLayout = activity.findViewById(R.id.routeReportItems)
    private val empty: View = activity.findViewById(R.id.routeReportEmpty)

    /** 마지막으로 그린 경로와 판정. 같으면 다시 그리지 않는다. */
    private var drawnFor: Pair<RouteTrip, Int>? = null

    init {
        activity.findViewById<View>(R.id.routeReportMissedCard).background = activity.cardBackground()
        activity.findViewById<View>(R.id.routeReportKeptCard).background = activity.cardBackground()
        activity.findViewById<View>(R.id.routeReportAsk).apply {
            background = activity.pillBackground(activity.getColor(R.color.text))
            setOnClickListener { onAsk() }
        }
    }

    fun show(screen: Screen) {
        root.visibility = if (screen == Screen.ROUTE_REPORT) View.VISIBLE else View.GONE
    }

    fun render(state: CoachState) {
        val trip = state.route ?: return
        val key = trip to state.todayEvents.size
        if (drawnFor == key) return
        drawnFor = key

        val events = trip.events(state.todayEvents)
        val missedCount = events.count { it.result == Result.MISSED }
        val report = buildReport(events, laws())

        title.text = activity.getString(R.string.route_report_title, trip.plan.destination)
        summary.text = routeSummary(trip, state.todayEvents)
        missed.text = numberEmphasis(activity.getString(R.string.route_report_missed_count, missedCount))
        missedHint.setText(if (missedCount == 0) R.string.route_report_missed_none_hint else R.string.route_report_missed_hint)
        kept.text = activity.getString(R.string.route_report_kept_count, report.kept)
        saved.text = activity.getString(R.string.route_report_saved, wonLabel(report.savedWon))

        val cards = routeReportItems(trip, state.todayEvents, laws())
        empty.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE
        items.removeAllViews()
        cards.forEachIndexed { index, item -> addItem(item, isLast = index == cards.lastIndex) }
    }

    /** 판정 카드 한 장. 경로 리포트 전용 route_report_item.xml을 쓴다. 놓침 = 빨강 !, 지킴 = 파랑 ✓. */
    private fun addItem(item: ReportItem, isLast: Boolean) {
        val row = activity.layoutInflater.inflate(R.layout.route_report_item, items, false)
        val tone = activity.getColor(if (item.kept) R.color.ok else R.color.miss)
        val toneSoft = activity.getColor(if (item.kept) R.color.ok_soft else R.color.miss_soft)
        row.findViewById<ImageView>(R.id.routeItemIcon).apply {
            setImageResource(if (item.kept) R.drawable.ic_kk_check else R.drawable.ic_kk_alert)
            imageTintList = ColorStateList.valueOf(tone)
            background = activity.rounded(toneSoft, radiusDp = 999)
        }
        row.findViewById<View>(R.id.routeItemLine).visibility = if (isLast) View.INVISIBLE else View.VISIBLE
        row.findViewById<View>(R.id.routeItemCard).background = activity.cardBackground()
        row.findViewById<TextView>(R.id.routeItemTitle).apply {
            text = item.title
            setTextColor(if (item.kept) activity.getColor(R.color.text) else tone)
        }
        row.findViewById<TextView>(R.id.routeItemDetail).text = item.detail
        row.findViewById<TextView>(R.id.routeItemArticle).chip(item.article, activity.getColor(R.color.surface_2))
        row.findViewById<TextView>(R.id.routeItemPenalty).chip(item.penalty, if (item.kept) activity.getColor(R.color.surface_2) else toneSoft)
        items.addView(row)
    }

    /** 칩 모양. 글자가 비었으면(룰 테이블에 없음) 숨긴다. */
    private fun TextView.chip(label: String, background: Int) {
        text = label
        visibility = if (label.isEmpty()) View.GONE else View.VISIBLE
        this.background = activity.rounded(background, radiusDp = CHIP_RADIUS_DP)
    }

    private companion object {
        const val CHIP_RADIUS_DP = 20
    }
}

/** 경로 리포트 "AI에게 묻기"가 법규 Q&A에 바로 묻는 질문. 적색 우회전 일시정지는 룰 테이블에 근거가 있다 (추천 질문 첫 번째). */
val ROUTE_REPORT_QUESTION: String = SUGGESTED_QUESTIONS.first()
