package kkam.app

import android.app.Activity
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 경로 미리보기 (screen_route_preview.xml). 출발 전 기어 P에서만 보인다.
 * 누를 것은 경로 데모의 "출발" 하나뿐이다 (실제 내비 경로에서는 숨기고 기어 D로 출발한다).
 * 값은 [buildRoutePreview]에서만 온다.
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
    private val points: TextView = activity.findViewById(R.id.routePoints)
    private val counts: LinearLayout = activity.findViewById(R.id.routeCounts)
    private val weakSource: TextView = activity.findViewById(R.id.routeWeakSource)
    private val weakLabel: TextView = activity.findViewById(R.id.routeWeakLabel)
    private val weakDetail: TextView = activity.findViewById(R.id.routeWeakDetail)
    private val sequence: TextView = activity.findViewById(R.id.routeSequence)

    /** 마지막으로 그린 미리보기. 같으면 다시 그리지 않는다. */
    private var drawn: RoutePreview? = null

    init {
        activity.findViewById<View>(R.id.routePointsCard).background = activity.cardBackground()
        activity.findViewById<View>(R.id.routeWeakCard).background = activity.cardBackground(activity.getColor(R.color.warn_soft))
        activity.findViewById<View>(R.id.routeSequenceCard).background = activity.cardBackground()
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
        if (preview == drawn) return
        drawn = preview
        draw(preview)
    }

    private fun draw(preview: RoutePreview) {
        destination.text = if (preview.distanceLabel.isEmpty()) {
            activity.getString(R.string.route_preview_destination_only, preview.destination)
        } else {
            activity.getString(R.string.route_preview_destination, preview.destination, preview.distanceLabel)
        }

        // 숫자는 크게, 회전이 없을 때 문장은 본문 크기로
        if (preview.coachPoints == 0) {
            points.setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.resources.getDimension(R.dimen.kk_text_body))
            points.text = activity.getString(R.string.route_preview_no_points)
        } else {
            points.setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.resources.getDimension(R.dimen.kk_text_display))
            points.text = numberEmphasis(activity.getString(R.string.route_preview_points_count, preview.coachPoints))
        }
        counts.removeAllViews()
        preview.counts.forEach(::addChip)

        val weak = preview.weakSpot
        weakSource.setText(
            when {
                weak == null -> R.string.route_preview_weak_none
                weak.fromToday -> R.string.route_preview_weak_today
                else -> R.string.route_preview_weak_diagnosis
            },
        )
        weakLabel.text = weak?.label ?: activity.getString(R.string.route_preview_weak_empty)
        weakDetail.text = when {
            weak == null -> ""
            preview.weakOnRoute == 0 -> activity.getString(R.string.route_preview_weak_not_on_route)
            else -> activity.getString(R.string.route_preview_weak_on_route, preview.weakOnRoute)
        }

        val stops = listOf(activity.getString(R.string.route_preview_start)) + preview.sequence +
            activity.getString(R.string.route_preview_end)
        sequence.text = stops.joinToString("  →  ")
    }

    /** 종류별 개수 칩. 예: "좌회전 2" */
    private fun addChip(label: String) {
        val chip = TextView(activity).apply {
            text = label
            textSize = CHIP_TEXT_SP
            setTextColor(activity.getColor(R.color.text))
            background = activity.pillBackground(activity.getColor(R.color.surface_2))
            setPadding(activity.dp(CHIP_PAD_H_DP), activity.dp(CHIP_PAD_V_DP), activity.dp(CHIP_PAD_H_DP), activity.dp(CHIP_PAD_V_DP))
        }
        counts.addView(
            chip,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = activity.dp(CHIP_GAP_DP)
            },
        )
    }

    private companion object {
        const val CHIP_TEXT_SP = 22f
        const val CHIP_PAD_H_DP = 20
        const val CHIP_PAD_V_DP = 10
        const val CHIP_GAP_DP = 12
    }
}
