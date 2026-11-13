package kkam.app

import android.app.Activity
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kkam.engine.model.LawEntry

/**
 * 법규 Q&A 화면 (기획서 UI-5, F8). 기어 P일 때만 보인다.
 * 어느 화면을 보일지는 MainActivity가 [Screen]으로 정하고, 이 클래스는 자기 화면만 그린다.
 *
 * 질문(입력칸 또는 추천 칩) → [answerer] → 답변 카드(답 2~3문장 + 근거 카드).
 * 답을 만드는 규칙은 LawQna.kt에 있다. 이 클래스는 그리기만 한다.
 */
class QnaScreen(
    private val activity: Activity,
    /** 지금은 룰 테이블 키워드 매칭(MainActivity가 넘긴다). Gleo AI가 붙으면 넘기는 것만 바꾼다 (LawQna.kt TODO). */
    private val answerer: QnaAnswerer,
) {
    private val root: View = activity.findViewById(R.id.qnaScreen)
    private val input: EditText = activity.findViewById(R.id.qnaInput)
    private val mic: View = activity.findViewById(R.id.qnaMic)
    private val ask: TextView = activity.findViewById(R.id.qnaAsk)
    private val chips: LinearLayout = activity.findViewById(R.id.qnaChips)
    private val empty: View = activity.findViewById(R.id.qnaEmpty)
    private val card: View = activity.findViewById(R.id.qnaAnswerCard)
    private val icon: ImageView = activity.findViewById(R.id.qnaAnswerIcon)
    private val questionText: TextView = activity.findViewById(R.id.qnaQuestion)
    private val disputed: TextView = activity.findViewById(R.id.qnaDisputed)
    private val answerText: TextView = activity.findViewById(R.id.qnaAnswerText)
    private val sourcesLabel: View = activity.findViewById(R.id.qnaSourcesLabel)
    private val sources: LinearLayout = activity.findViewById(R.id.qnaSources)

    init {
        paintStaticViews()
        SUGGESTED_QUESTIONS.forEach(::addChip)

        ask.setOnClickListener { askTyped() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) askTyped()
            actionId == EditorInfo.IME_ACTION_SEARCH
        }
        // STT는 자리만. TODO(앱 담당): Pleos 음성 인식 SDK 확인 후 받은 문장을 ask()에 넘긴다.
        mic.setOnClickListener { Toast.makeText(activity, R.string.qna_mic_soon, Toast.LENGTH_SHORT).show() }
    }

    fun show(screen: Screen) {
        root.visibility = if (screen == Screen.QNA) View.VISIBLE else View.GONE
    }

    /** 다른 화면에서 질문을 들고 왔을 때 (경로 리포트 "AI에게 묻기"). 입력칸에 넣고 바로 답한다. */
    fun askNow(question: String) {
        input.setText(question)
        ask(question)
    }

    private fun askTyped() {
        val question = input.text.toString().trim()
        if (question.isNotEmpty()) ask(question)
    }

    private fun ask(question: String) {
        hideKeyboard()
        render(answerer.answer(question))
    }

    /** 답변 카드. 근거가 없으면 회색 i 아이콘 + 회색 글자로 톤을 낮추고 근거 칸을 숨긴다. */
    private fun render(answer: QnaAnswer) {
        empty.visibility = View.GONE
        card.visibility = View.VISIBLE

        questionText.text = answer.question
        answerText.text = answer.text
        disputed.visibility = if (answer.disputed) View.VISIBLE else View.GONE

        val tone = activity.getColor(if (answer.hasBasis) R.color.ok else R.color.muted)
        icon.setImageResource(if (answer.hasBasis) R.drawable.ic_law else R.drawable.ic_info)
        icon.imageTintList = ColorStateList.valueOf(tone)
        icon.background = activity.rounded(
            activity.getColor(if (answer.hasBasis) R.color.ok_soft else R.color.surface_2),
            radiusDp = ICON_RADIUS_DP,
        )
        answerText.setTextColor(activity.getColor(if (answer.hasBasis) R.color.text else R.color.muted))

        sources.removeAllViews()
        sourcesLabel.visibility = if (answer.hasBasis) View.VISIBLE else View.GONE
        answer.sources.forEachIndexed { index, law -> addSourceCard(law, first = index == 0) }
    }

    private fun addSourceCard(law: LawEntry, first: Boolean) {
        val view = LayoutInflater.from(activity).inflate(R.layout.qna_source_card, sources, false)
        view.background = activity.rounded(activity.getColor(R.color.surface_2), radiusDp = SOURCE_RADIUS_DP)
        view.findViewById<TextView>(R.id.sourceId).apply {
            text = law.id
            background = activity.pillBackground(activity.getColor(R.color.ok_soft))
        }
        view.findViewById<TextView>(R.id.sourceTopic).text = law.topic
        view.findViewById<TextView>(R.id.sourceArticle).text = law.article
        view.findViewById<TextView>(R.id.sourcePenalty).text = law.penalty
        view.findViewById<TextView>(R.id.sourceOrigin).text =
            activity.getString(R.string.qna_source) + " · " + sourceName(law.sourceUrl)
        (view.layoutParams as LinearLayout.LayoutParams).marginStart = if (first) 0 else activity.dp(GAP_DP)
        sources.addView(view)
    }

    private fun addChip(question: String) {
        val chip = LayoutInflater.from(activity).inflate(R.layout.qna_chip, chips, false) as TextView
        chip.text = question
        chip.background = activity.pillBackground(activity.getColor(R.color.surface_2), activity.getColor(R.color.line))
        chip.setOnClickListener {
            input.setText(question)
            ask(question)
        }
        (chip.layoutParams as LinearLayout.LayoutParams).marginEnd = activity.dp(GAP_DP)
        chips.addView(chip)
    }

    private fun paintStaticViews() {
        input.background = activity.pillBackground(activity.getColor(R.color.surface), activity.getColor(R.color.line))
        mic.background = activity.pillBackground(activity.getColor(R.color.surface_2))
        ask.background = activity.pillBackground(activity.getColor(R.color.text))
        card.background = activity.cardBackground()
        disputed.background = activity.pillBackground(activity.getColor(R.color.warn_soft))
    }

    private fun hideKeyboard() {
        input.clearFocus()
        activity.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private companion object {
        const val GAP_DP = 16
        const val ICON_RADIUS_DP = 16
        const val SOURCE_RADIUS_DP = 20
    }
}
