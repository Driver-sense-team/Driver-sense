package kkam.app

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView

/**
 * 교정 진행·주간 코칭 화면 (기획서 UI-4, F6·F7, screen_progress.xml). 기어 P일 때만 보인다.
 * 어느 화면을 보일지는 MainActivity가 [Screen]으로 정하고, 이 클래스는 자기 화면만 그린다.
 * 값은 [ProgressSource]에서만 온다. 지금은 데모 고정값([DemoProgressSource]).
 *
 * 색 = 의미: 주황 warn = 할 일(교정 중, 다음 주 집중), 파랑 ok = 지킴(유지·졸업, 준수율 막대), 빨강 miss = 놓친 곳.
 */
class ProgressScreen(
    private val activity: Activity,
    source: ProgressSource = DemoProgressSource,
) {
    private val root: View = activity.findViewById(R.id.progressScreen)

    init {
        draw(source.load())
    }

    fun show(screen: Screen) {
        root.visibility = if (screen == Screen.PROGRESS) View.VISIBLE else View.GONE
    }

    private fun draw(progress: WeeklyProgress) {
        text(R.id.progressPeriod).text = progress.periodLabel
        listOf(R.id.progressWeekCard, R.id.progressCoachCard, R.id.progressMissedCard).forEach {
            activity.findViewById<View>(it).background = activity.cardBackground()
        }
        val habits: LinearLayout = activity.findViewById(R.id.progressHabits)
        progress.habits.forEach { addHabitCard(habits, it) }
        drawWeek(progress)
        drawCoaching(progress)
        val missed: LinearLayout = activity.findViewById(R.id.progressMissed)
        progress.topMissed.forEachIndexed { index, place -> addMissedRow(missed, index + 1, place) }
    }

    // ===== 습관별 졸업 진행 카드 =====

    private fun addHabitCard(parent: LinearLayout, habit: HabitProgress) {
        val card = activity.layoutInflater.inflate(R.layout.progress_habit_card, parent, false)
        if (parent.childCount > 0) (card.layoutParams as LinearLayout.LayoutParams).marginStart = activity.dp(16)
        card.background = activity.cardBackground()
        val stageColor = stageColor(habit.stage)

        card.findViewById<TextView>(R.id.habitProgressName).text = habit.habit.label
        drawStageChips(card.findViewById(R.id.habitProgressStages), habit.stage)
        card.findViewById<TextView>(R.id.habitProgressFraction).text =
            numberEmphasis(activity.getString(R.string.progress_recent_fraction, habit.keptInRecent, RECENT_WINDOW))
        card.findViewById<TextView>(R.id.habitProgressRecent).text = habit.recentLabel
        drawSegments(card.findViewById(R.id.habitProgressBar), habit.progress, stageColor)
        card.findViewById<TextView>(R.id.habitProgressNext).apply {
            text = habit.nextLabel
            setTextColor(stageColor)
        }
        parent.addView(card)
    }

    /** 교정 → 유지 → 졸업 칩. 지금 단계만 색을 채우고, 지난 단계는 옅게, 남은 단계는 회색. */
    private fun drawStageChips(parent: LinearLayout, current: HabitStage) {
        HabitStage.entries.forEach { stage ->
            if (stage.ordinal > 0) parent.addView(styled(R.style.Kk_Caption).apply { text = " › " })
            val chip = styled(R.style.Kk_Caption).apply {
                text = stage.label
                setPadding(activity.dp(18), activity.dp(6), activity.dp(18), activity.dp(6))
            }
            when {
                stage == current -> {
                    chip.background = activity.pillBackground(stageColor(stage))
                    chip.setTextColor(color(R.color.on_accent))
                    chip.setTypeface(null, Typeface.BOLD)
                }
                stage.ordinal < current.ordinal -> {
                    chip.background = activity.pillBackground(color(R.color.ok_soft))
                    chip.setTextColor(color(R.color.ok))
                }
                else -> chip.background = activity.pillBackground(color(R.color.surface_2))
            }
            parent.addView(chip)
        }
    }

    /**
     * 졸업까지 막대를 [GRADUATION_WEEKS] + 1칸으로 그린다. 칸마다 [progress]만큼 채운다.
     * 예: progress 0.5 → 앞 두 칸 꽉, 뒤 두 칸 빈칸.
     */
    private fun drawSegments(parent: LinearLayout, progress: Float, fillColor: Int) {
        val segments = GRADUATION_WEEKS + 1
        repeat(segments) { i ->
            val fill = (progress * segments - i).coerceIn(0f, 1f)
            val segment = horizontalFill(fill, fillColor, radiusDp = 999)
            segment.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                if (i > 0) marginStart = activity.dp(8)
            }
            parent.addView(segment)
        }
    }

    // ===== 이번 주 준수율 =====

    private fun drawWeek(progress: WeeklyProgress) {
        text(R.id.progressWeekPercent).text = numberEmphasis("${progress.weekPercent} %")
        text(R.id.progressChange).apply {
            val change = progress.changeFromDiagnosis
            val up = change >= 0
            text = activity.getString(
                if (up) R.string.progress_change_up else R.string.progress_change_down,
                progress.diagnosisPercent,
                change,
            )
            setTextColor(color(if (up) R.color.ok else R.color.warn))
            background = activity.pillBackground(color(if (up) R.color.ok_soft else R.color.warn_soft))
        }

        val percents: LinearLayout = activity.findViewById(R.id.progressBarPercents)
        val bars: LinearLayout = activity.findViewById(R.id.progressBars)
        val days: LinearLayout = activity.findViewById(R.id.progressBarDays)
        val todayIndex = progress.days.lastIndex
        progress.days.forEachIndexed { index, day ->
            val today = index == todayIndex
            percents.addView(columnLabel(day.percent?.let { "$it%" } ?: activity.getString(R.string.progress_no_drive), today))
            bars.addView(dayBar(day.percent, today))
            days.addView(columnLabel(if (today) activity.getString(R.string.progress_today) else day.day, today))
        }

        // 진단 기준선: 막대 칸의 아래에서 진단% 높이.
        activity.findViewById<View>(R.id.progressLineAbove).setWeight((100 - progress.diagnosisPercent).toFloat())
        activity.findViewById<View>(R.id.progressLineBelow).setWeight(progress.diagnosisPercent.toFloat())
        text(R.id.progressLineLegend).text = activity.getString(R.string.progress_diagnosis_line, progress.diagnosisPercent)

        activity.findViewById<View>(R.id.progressConversionTile).background = activity.cardBackground(color(R.color.surface_2))
        activity.findViewById<View>(R.id.progressSavedTile).background = activity.cardBackground(color(R.color.surface_2))
        text(R.id.progressConversion).text = numberEmphasis("${progress.guideConversionPercent} %")
        // 금액은 "33" 크게 + "만 원" 작게. 오늘 리포트의 아낀 범칙금과 같은 모양이다.
        text(R.id.progressSaved).text = digitsEmphasis(wonLabel(progress.savedWon))
    }

    /** 하루 막대. 오늘은 진한 파랑, 다른 날은 옅은 파랑. 주행이 없으면 빈 칸만. */
    private fun dayBar(percent: Int?, today: Boolean): View {
        val fill = percent ?: 0
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = activity.rounded(color(R.color.surface_2), radiusDp = 16)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = activity.dp(BAR_GAP_DP)
                marginEnd = activity.dp(BAR_GAP_DP)
            }
        }
        column.addView(Space(activity), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, (100 - fill).toFloat()))
        column.addView(
            View(activity).apply {
                setBackgroundColor(color(R.color.ok))
                alpha = if (today) 1f else OTHER_DAY_ALPHA
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, fill.toFloat()),
        )
        return column
    }

    /** 막대 위·아래 글자 (퍼센트, 요일). 오늘만 진한 글씨(text) 굵게. */
    private fun columnLabel(label: String, today: Boolean) = styled(R.style.Kk_Caption).apply {
        text = label
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        if (today) {
            setTextColor(color(R.color.text))
            setTypeface(null, Typeface.BOLD)
        }
    }

    // ===== AI 코칭, 자주 놓친 곳 =====

    private fun drawCoaching(progress: WeeklyProgress) {
        activity.findViewById<View>(R.id.progressCoachIcon).background = activity.pillBackground(color(R.color.ok_soft))
        text(R.id.progressCoaching).text = progress.coaching
        activity.findViewById<View>(R.id.progressFocusBox).background = activity.rounded(color(R.color.warn_soft), radiusDp = 20)
        text(R.id.progressFocus).text = progress.nextFocus
    }

    private fun addMissedRow(parent: LinearLayout, rank: Int, place: MissedPlace) {
        val row = activity.layoutInflater.inflate(R.layout.progress_missed_row, parent, false)
        row.findViewById<TextView>(R.id.missedRank).text = rank.toString()
        row.findViewById<TextView>(R.id.missedName).text = place.name
        row.findViewById<TextView>(R.id.missedCount).text = activity.getString(R.string.progress_missed_count, place.count)
        parent.addView(row)
    }

    // ===== 그리기 도구 =====

    /** 가로로 [fill](0~1)만큼 채운 둥근 칸. */
    private fun horizontalFill(fill: Float, fillColor: Int, radiusDp: Int): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        background = activity.rounded(color(R.color.surface_2), radiusDp)
        clipToOutline = true
        addView(View(activity).apply { setBackgroundColor(fillColor) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, fill))
        addView(Space(activity), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - fill))
    }

    /** 교정은 할 일(주황), 유지·졸업은 지킴(파랑). */
    private fun stageColor(stage: HabitStage) = color(if (stage == HabitStage.CORRECT) R.color.warn else R.color.ok)

    /** 코드에서 만드는 글자에 Kk.* 스타일을 입힌다 (스타일의 layout_* 값은 적용되지 않는다). */
    private fun styled(style: Int) = TextView(activity, null, 0, style)

    private fun View.setWeight(weight: Float) {
        layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { this.weight = weight }
    }

    private fun text(id: Int): TextView = activity.findViewById(id)

    private fun color(id: Int) = activity.getColor(id)

    private companion object {
        /** 막대 사이 간격의 절반. */
        const val BAR_GAP_DP = 10

        /** 오늘이 아닌 날 막대의 투명도. 오늘을 강조한다. */
        const val OTHER_DAY_ALPHA = 0.45f
    }
}
