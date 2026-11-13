package kkam.app

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 경로 데모 목적지 검색창 (route_search.xml, 지도 위 왼쪽 위). 에뮬레이터 내비 대신 앱 지도를 내비로 쓴다.
 * 입력하면 [searchDestinations] 결과를 목록으로 보여주고, 하나를 누르면 [onSelect]로 넘긴다 (경로 데모 시작).
 * 출발지는 현재 위치로 보여주는 경로 데모 파일의 start (세종대학교)다.
 */
class DestinationSearch(
    private val activity: Activity,
    private val onSelect: (DemoDestination) -> Unit,
) {
    private val root: View = activity.findViewById(R.id.routeSearch)
    private val from: TextView = activity.findViewById(R.id.routeSearchFrom)
    private val input: EditText = activity.findViewById(R.id.routeSearchInput)
    private val results: LinearLayout = activity.findViewById(R.id.routeSearchResults)

    init {
        input.background = activity.pillBackground(activity.getColor(R.color.surface), activity.getColor(R.color.line))
        results.background = activity.cardBackground()
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = showResults(s?.toString().orEmpty())
        })
        // 검색 키: 결과가 하나면 바로 고른다
        input.setOnEditorActionListener { _, actionId, _ ->
            val only = searchDestinations(input.text.toString()).singleOrNull()
            if (actionId == EditorInfo.IME_ACTION_SEARCH && only != null) select(only)
            actionId == EditorInfo.IME_ACTION_SEARCH
        }
    }

    /** 검색창을 연다. 입력과 목록은 비운다. */
    fun open(startName: String) {
        from.text = activity.getString(R.string.route_search_from, startName)
        input.setText("")
        results.visibility = View.GONE
        root.visibility = View.VISIBLE
    }

    fun close() {
        if (root.visibility == View.GONE) return
        hideKeyboard()
        root.visibility = View.GONE
    }

    private fun showResults(query: String) {
        results.removeAllViews()
        if (query.isBlank()) {
            results.visibility = View.GONE
            return
        }
        val found = searchDestinations(query)
        if (found.isEmpty()) {
            results.addView(row(activity.getString(R.string.route_search_empty), null))
        }
        found.forEach { destination -> results.addView(row(destination.name, destination.area).apply { setOnClickListener { select(destination) } }) }
        results.visibility = View.VISIBLE
    }

    /** 목록 한 줄: 이름(본문) + 지역(작게). */
    private fun row(title: String, subtitle: String?): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = activity.dp(ROW_MIN_HEIGHT_DP)
        setPadding(activity.dp(ROW_PADDING_DP), activity.dp(ROW_PADDING_V_DP), activity.dp(ROW_PADDING_DP), activity.dp(ROW_PADDING_V_DP))
        addView(TextView(activity, null, 0, R.style.Kk_Body).apply { text = title })
        if (subtitle != null) addView(TextView(activity, null, 0, R.style.Kk_Caption).apply { text = subtitle })
    }

    private fun select(destination: DemoDestination) {
        close()
        onSelect(destination)
    }

    private fun hideKeyboard() {
        input.clearFocus()
        activity.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private companion object {
        const val ROW_MIN_HEIGHT_DP = 96
        const val ROW_PADDING_DP = 32
        const val ROW_PADDING_V_DP = 16
    }
}
