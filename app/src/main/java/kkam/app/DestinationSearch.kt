package kkam.app

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 경로 데모 목적지 검색창 (route_search.xml, 지도 위 왼쪽 위). 에뮬레이터 내비 대신 앱 지도를 내비로 쓴다.
 * 열면 키보드 없이 "최근 검색" 목록([RECENT_DESTINATIONS])을 바로 보여주고, 하나를 누르면 [onSelect]로 넘긴다 (경로 미리보기).
 * 검색창은 글자를 입력받지 않는 표시용이라 눌러도 키보드가 올라오지 않는다 (시연 화면을 키보드가 덮지 않게).
 * 출발지는 현재 위치로 보여주는 경로 데모 파일의 start (세종대학교)다.
 */
class DestinationSearch(
    private val activity: Activity,
    private val onSelect: (DemoDestination) -> Unit,
) {
    private val root: View = activity.findViewById(R.id.routeSearch)
    private val from: TextView = activity.findViewById(R.id.routeSearchFrom)
    private val input: TextView = activity.findViewById(R.id.routeSearchInput)
    private val results: LinearLayout = activity.findViewById(R.id.routeSearchResults)

    init {
        input.background = activity.pillBackground(activity.getColor(R.color.surface), activity.getColor(R.color.line))
        results.background = activity.cardBackground()
    }

    /** 검색창을 연다. 최근 검색 목록을 바로 보여준다. */
    fun open(startName: String) {
        from.text = activity.getString(R.string.route_search_from, startName)
        showRecent()
        root.visibility = View.VISIBLE
    }

    fun close() {
        root.visibility = View.GONE
    }

    /** "최근 검색" 제목 아래에 목적지를 한 줄씩 놓는다. */
    private fun showRecent() {
        results.removeAllViews()
        results.addView(header(activity.getString(R.string.route_search_recent)))
        RECENT_DESTINATIONS.forEach { destination ->
            results.addView(row(destination).apply { setOnClickListener { select(destination) } })
        }
        results.visibility = View.VISIBLE
    }

    /** 목록 제목: "최근 검색" (작게). */
    private fun header(title: String): View = TextView(activity, null, 0, R.style.Kk_Caption).apply {
        text = title
        setPadding(activity.dp(ROW_PADDING_DP), activity.dp(HEADER_PADDING_TOP_DP), activity.dp(ROW_PADDING_DP), 0)
    }

    /** 목록 한 줄: 시계 아이콘 + 이름(본문) + 지역(작게). */
    private fun row(destination: DemoDestination): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = activity.dp(ROW_MIN_HEIGHT_DP)
        setPadding(activity.dp(ROW_PADDING_DP), activity.dp(ROW_PADDING_V_DP), activity.dp(ROW_PADDING_DP), activity.dp(ROW_PADDING_V_DP))
        val icon = ImageView(activity).apply {
            setImageResource(R.drawable.ic_recent)
            setColorFilter(activity.getColor(R.color.muted))
        }
        val iconParams = LinearLayout.LayoutParams(activity.dp(ICON_SIZE_DP), activity.dp(ICON_SIZE_DP))
        iconParams.marginEnd = activity.dp(ICON_GAP_DP)
        addView(icon, iconParams)
        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity, null, 0, R.style.Kk_Body).apply { text = destination.name })
            addView(TextView(activity, null, 0, R.style.Kk_Caption).apply { text = destination.area })
        }
        // 남은 너비를 글자가 다 쓴다 (weight 1)
        addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun select(destination: DemoDestination) {
        close()
        onSelect(destination)
    }

    private companion object {
        const val ROW_MIN_HEIGHT_DP = 96
        const val ROW_PADDING_DP = 32
        const val ROW_PADDING_V_DP = 16
        const val HEADER_PADDING_TOP_DP = 24
        const val ICON_SIZE_DP = 40
        const val ICON_GAP_DP = 24
    }
}
