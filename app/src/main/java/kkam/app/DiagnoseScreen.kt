package kkam.app

import android.app.Activity
import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 진단 리포트(screen_diagnose) 화면을 그린다 (기획서 UI-3).
 * 값은 [Diagnosis]에서만 온다. 어느 화면을 보일지는 MainActivity가 [Screen]으로 정한다.
 *
 * 색 = 의미: 주황 = 전국 평균보다 낮음(고칠 일), 파랑 = 평균 이상.
 * 색만으로 구분하지 않게 ! 아이콘과 "평균보다 낮음" 칩을 같이 쓴다.
 */
class DiagnoseScreen(
    private val activity: Activity,
    private val diagnosis: Diagnosis,
) {
    private val root: View = activity.findViewById(R.id.diagnoseScreen)
    private val averageLabel = formatPercent(diagnosis.nationalAveragePercent)

    init {
        drawDiagnosis()
    }

    fun show(screen: Screen) {
        root.visibility = if (screen == Screen.DIAGNOSE) View.VISIBLE else View.GONE
    }

    private fun drawDiagnosis() {
        activity.findViewById<TextView>(R.id.diagnosePeriod).text = diagnosis.periodLabel
        activity.findViewById<TextView>(R.id.diagnoseAverage).text =
            activity.getString(R.string.parked_diagnose_average_legend, averageLabel)

        // 히어로: 가장 낮은 상황이 평균보다 낮으면 주황 !, 아니면 파랑 ✓
        val lowestBelow = diagnosis.belowAverage.isNotEmpty()
        val tone = color(if (lowestBelow) R.color.warn else R.color.ok)
        activity.findViewById<View>(R.id.diagnoseHero).background = activity.cardBackground()
        activity.findViewById<ImageView>(R.id.diagnoseHeroIcon).apply {
            setImageResource(if (lowestBelow) R.drawable.ic_kk_alert else R.drawable.ic_kk_check)
            imageTintList = ColorStateList.valueOf(tone)
            background = activity.pillBackground(color(if (lowestBelow) R.color.warn_soft else R.color.ok_soft))
        }
        activity.findViewById<TextView>(R.id.diagnoseHeadline).text = headlineText(diagnosis.headline, tone)

        activity.findViewById<View>(R.id.diagnoseRatesCard).background = activity.cardBackground()
        val rows: LinearLayout = activity.findViewById(R.id.diagnoseRows)
        diagnosis.rates.forEach { rows.addView(rateRow(rows, it)) }
    }

    /** "좌회전 깜빡이 62%, ..."에서 퍼센트 숫자만 크게, [tone] 색으로 칠한다. */
    private fun headlineText(headline: String, tone: Int): CharSequence {
        val match = Regex("""\d+%""").find(headline) ?: return headline
        val start = match.range.first
        val end = match.range.last + 1
        return SpannableString(headline).apply {
            setSpan(ForegroundColorSpan(tone), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(HEADLINE_NUMBER_SCALE), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** 준수율 막대 한 줄. 평균보다 낮으면 주황 막대·숫자 + "! 평균보다 낮음" 칩. */
    private fun rateRow(parent: LinearLayout, rate: SituationRate): View {
        val row = activity.layoutInflater.inflate(R.layout.diagnose_row, parent, false)
        val below = rate.percent < diagnosis.nationalAveragePercent
        val tone = color(if (below) R.color.warn else R.color.ok)

        row.findViewById<TextView>(R.id.rowLabel).text = rate.habit.label
        row.findViewById<TextView>(R.id.rowCount).text = activity.getString(R.string.diagnose_count, rate.kept, rate.total)
        row.findViewById<TextView>(R.id.rowChip).apply {
            text = if (below) "! " + activity.getString(R.string.parked_diagnose_below) else activity.getString(R.string.parked_diagnose_above)
            setTextColor(if (below) tone else color(R.color.muted))
            background = activity.pillBackground(color(if (below) R.color.warn_soft else R.color.surface_2))
        }
        row.findViewById<TextView>(R.id.rowPercent).apply {
            text = numberEmphasis("${rate.percent} %")
            setTextColor(tone)
        }
        row.findViewById<View>(R.id.rowTrack).apply {
            background = activity.rounded(color(R.color.surface_2), radiusDp = BAR_RADIUS_DP)
            clipToOutline = true
        }
        row.findViewById<View>(R.id.rowFill).apply {
            setBackgroundColor(tone)
            setWeight(rate.percent.toFloat())
        }
        row.findViewById<View>(R.id.rowRest).setWeight((100 - rate.percent).toFloat())
        row.findViewById<View>(R.id.rowAverageBefore).setWeight(diagnosis.nationalAveragePercent.toFloat())
        row.findViewById<View>(R.id.rowAverageAfter).setWeight((100 - diagnosis.nationalAveragePercent).toFloat())
        return row
    }

    private fun View.setWeight(weight: Float) {
        layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { this.weight = weight }
    }

    private fun color(id: Int) = activity.getColor(id)

    /** 75.7 → "75.7", 76.0 → "76" */
    private fun formatPercent(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private companion object {
        const val BAR_RADIUS_DP = 16
        /** 히어로 문구에서 퍼센트 숫자를 몇 배로 키울지 */
        const val HEADLINE_NUMBER_SCALE = 1.6f
    }
}
