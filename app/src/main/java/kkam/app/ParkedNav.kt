package kkam.app

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 기어 P 화면 왼쪽 레일. 리포트·진단·교정 진행·Q&A·모드를 오간다.
 * 주행 화면에서는 숨긴다 (주행 중 터치 조작 없음, 기획서 5.4).
 */
class ParkedNav(
    private val activity: Activity,
    /** 항목을 눌렀을 때. 실제로 열지는 MainActivity가 [openScreen]으로 정한다. */
    private val onSelect: (Screen) -> Unit,
) {
    private val rail: LinearLayout = activity.findViewById(R.id.navRail)
    private val items = mutableMapOf<Screen, TextView>()

    init {
        ITEMS.forEach { (target, label) -> addItem(target, label) }
    }

    /** [screen]이 정차 화면이면 레일을 보이고 그 항목을 칠한다. */
    fun show(screen: Screen) {
        rail.visibility = if (screen.parkedOnly) View.VISIBLE else View.GONE
        items.forEach { (target, item) ->
            val on = target == screen
            item.background = if (on) activity.pillBackground(activity.getColor(R.color.surface_2)) else null
            item.setTextColor(activity.getColor(if (on) R.color.text else R.color.muted))
        }
    }

    private fun addItem(target: Screen, label: Int) {
        val item = TextView(activity).apply {
            setText(label)
            gravity = Gravity.CENTER
            textSize = ITEM_TEXT_SP
            setOnClickListener { onSelect(target) }
        }
        rail.addView(
            item,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(ITEM_HEIGHT_DP)).apply {
                bottomMargin = activity.dp(ITEM_GAP_DP)
            },
        )
        items[target] = item
    }

    private companion object {
        const val ITEM_TEXT_SP = 22f
        const val ITEM_HEIGHT_DP = 88
        const val ITEM_GAP_DP = 8

        /** 레일 순서. */
        val ITEMS = listOf(
            Screen.REPORT to R.string.nav_report,
            Screen.DIAGNOSE to R.string.nav_diagnose,
            Screen.PROGRESS to R.string.nav_progress,
            Screen.QNA to R.string.nav_qna,
            Screen.MODE to R.string.nav_mode,
        )
    }
}
