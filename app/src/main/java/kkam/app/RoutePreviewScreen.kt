package kkam.app

import android.app.Activity
import android.view.View
import android.widget.TextView
import java.time.LocalTime

/**
 * 경로 미리보기 (screen_route_preview.xml). 출발 전 기어 P에서만 보인다.
 * 보통 내비의 경로 요약처럼 목적지, 예상 소요 시간, 거리, 도착 예정 시각을 크게 보여주고,
 * 코치 정보는 그 아래 한 줄로 둔다.
 * 누를 것은 경로 데모의 "출발" 하나뿐이다 (실제 내비 경로에서는 숨기고 기어 D로 출발한다).
 * 값은 [buildRoutePreview]에서만 온다. 도착 시각만 지금 시각으로 계산한다 (판정 엔진이 아니라 화면이라 시각을 써도 된다).
 */
class RoutePreviewScreen(
    private val activity: Activity,
    /** 약한 곳을 고를 때 오늘 놓친 기록이 없으면 쓰는 진단 값. */
    private val diagnosis: () -> Diagnosis,
    /** 경로 데모의 "출발". 실제 내비 경로에서는 버튼이 숨어 있어 불리지 않는다. */
    private val onDepart: () -> Unit,
) {
    private val depart: View = activity.findViewById(R.id.routeDepart)
    private val hint: View = activity.findViewById(R.id.routePreviewHint)
    private val root: View = activity.findViewById(R.id.routePreviewScreen)
    private val destination: TextView = activity.findViewById(R.id.routeDestination)
    private val duration: TextView = activity.findViewById(R.id.routeDuration)
    private val distanceEta: TextView = activity.findViewById(R.id.routeDistanceEta)
    private val coachLine: TextView = activity.findViewById(R.id.routeCoachLine)

    /** 마지막으로 그린 미리보기와 도착 시각 문구. 같으면 다시 그리지 않는다. */
    private var drawn: Pair<RoutePreview, String>? = null

    init {
        activity.findViewById<View>(R.id.routeSummaryCard).background = activity.cardBackground()
        depart.background = activity.pillBackground(activity.getColor(R.color.ok))
        depart.setOnClickListener { onDepart() }
    }

    /** 경로 데모가 출발을 기다릴 때만 "출발" 버튼을 보이고, 기어 안내 문구는 숨긴다. */
    fun showDepart(waiting: Boolean) {
        depart.visibility = if (waiting) View.VISIBLE else View.GONE
        hint.visibility = if (waiting) View.GONE else View.VISIBLE
    }

    fun show(screen: Screen) {
        root.visibility = if (screen == Screen.ROUTE_PREVIEW) View.VISIBLE else View.GONE
    }

    fun render(state: CoachState) {
        val trip = state.route ?: return
        // 경로를 붙인 뒤의 판정은 미리보기에 넣지 않는다 (출발 전 기록만 본다).
        val preview = buildRoutePreview(trip.plan, state.todayEvents.take(trip.firstEventIndex), diagnosis())
        val eta = preview.minutes?.let { arrivalLabel(LocalTime.now(), it) } ?: ""
        if (drawn == preview to eta) return
        drawn = preview to eta
        draw(preview, eta)
    }

    private fun draw(preview: RoutePreview, eta: String) {
        destination.text = preview.destination

        val minutes = preview.minutes
        duration.text = if (minutes == null) activity.getString(R.string.route_preview_no_time) else durationLabel(minutes)
        distanceEta.text = listOf(preview.distanceLabel, eta).filter { it.isNotEmpty() }.let { parts ->
            if (parts.size == 2) activity.getString(R.string.route_preview_distance_eta, parts[0], parts[1]) else parts.joinToString("")
        }

        val weak = preview.weakSpot
        coachLine.text = when {
            preview.coachPoints == 0 -> activity.getString(R.string.route_preview_coach_none)
            weak != null && preview.weakOnRoute > 0 ->
                activity.getString(R.string.route_preview_coach_weak, preview.coachPoints, weak.label, preview.weakOnRoute)
            else -> activity.getString(R.string.route_preview_coach, preview.coachPoints)
        }
    }
}
