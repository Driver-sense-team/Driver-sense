package kkam.app

import android.animation.ArgbEvaluator
import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/**
 * 코치 카드(주행 화면)와 내비 위 팝업이 같이 쓰는 색 규칙.
 * 카드는 흰 바탕에 검정 글자다. 상태 색은 칠할 수 있는 곳에 모두 쓴다:
 * 굵은 테두리, 방향 그림 원, 행동 알약, 깜빡이 칩, 레버 그림, 카드 밑 한 줄 문구.
 *
 * | 모습 | 테두리 | 방향 그림 원 | 행동 알약 | 언제 |
 * | --- | --- | --- | --- | --- |
 * | IDLE | 연회색 | 연회색 원 + 회색 그림 | 숨김 | 안내 대상 없음 |
 * | WARN | 진한 노랑 | 노랑 원 + 검정 그림 | 노랑 "! 지금 켜세요" | 지금 켜야 함 |
 * | OK | 진한 파랑 | 파랑 원 + 검정 그림 | 파랑 "✓ 좋아요" | 켰음 |
 * | MISS | 진한 빨강 | 빨강 원 + 검정 그림 | 빨강 "X 다음엔 미리 켜요" | 판정 지점을 꺼진 채 지남 |
 *
 * 색만으로 구분하지 않게 알약 아이콘이 상태마다 다르다.
 */
enum class CardLook { IDLE, WARN, OK, MISS }

/** 주행 화면 상태 → 모습. 판정 지점을 주황인 채로 지나 고정되면 놓침(MISS)이다. */
fun CoachState.look(): CardLook = when {
    tone == Tone.IDLE -> CardLook.IDLE
    tone == Tone.OK -> CardLook.OK
    checkedTone == Tone.WARN -> CardLook.MISS
    else -> CardLook.WARN
}

/** 팝업은 놓침 때 숨으므로(PopupPolicy) 주황·파랑 둘뿐이다. */
fun PopupTone.look(): CardLook = if (this == PopupTone.OK) CardLook.OK else CardLook.WARN

/** 행동 알약 아이콘. 상태마다 모양이 다르다. */
fun CardLook.badgeIconRes(): Int = when (this) {
    CardLook.OK -> R.drawable.ic_check
    CardLook.MISS -> R.drawable.ic_miss
    CardLook.IDLE, CardLook.WARN -> R.drawable.ic_alert
}

/**
 * 카드 한 장에 칠할 색.
 * accent = 상태 색 채움 (그림 원, 알약, 켜진 깜빡이 칩), edge = 흰 바탕에서 또렷한 진한 상태 색
 * (카드 테두리, 꺼진 깜빡이 칩 테두리·화살표, 레버 그림), iconBg·iconInk = 방향 그림 원과 그림,
 * note = 카드 밑 한 줄 문구 색 (앱 바탕 위).
 */
data class CardPalette(
    val accent: Int,
    val edge: Int,
    val iconBg: Int,
    val iconInk: Int,
    val note: Int,
) {
    /** 두 색 사이 중간값. fraction 0이면 this, 1이면 to. 상태가 바뀔 때 한 번 페이드하는 데 쓴다. */
    fun mix(to: CardPalette, fraction: Float) = CardPalette(
        accent = blend(accent, to.accent, fraction),
        edge = blend(edge, to.edge, fraction),
        iconBg = blend(iconBg, to.iconBg, fraction),
        iconInk = blend(iconInk, to.iconInk, fraction),
        note = blend(note, to.note, fraction),
    )

    private fun blend(a: Int, b: Int, fraction: Float) = ARGB.evaluate(fraction, a, b) as Int

    private companion object {
        val ARGB = ArgbEvaluator()
    }
}

fun Context.palette(look: CardLook): CardPalette = when (look) {
    CardLook.IDLE -> CardPalette(
        accent = getColor(R.color.ink_soft),
        edge = getColor(R.color.card_light_line),
        iconBg = getColor(R.color.card_light_2),
        iconInk = getColor(R.color.ink_soft),
        note = getColor(R.color.text),
    )
    CardLook.WARN -> accentPalette(R.color.coach_warn, R.color.warn_deep, R.color.warn)
    CardLook.OK -> accentPalette(R.color.coach_ok, R.color.ok_deep, R.color.ok)
    CardLook.MISS -> accentPalette(R.color.coach_miss, R.color.miss_deep, R.color.miss)
}

/**
 * 카드 안(채움·원·테두리)은 밝은 코치 전용 색, 카드 밖 한 줄 문구(note)는 앱 바탕 위 글자라
 * 대비가 충분한 앱 공통 상태 색(warn/ok/miss)을 쓴다.
 */
private fun Context.accentPalette(accent: Int, deep: Int, note: Int) = CardPalette(
    accent = getColor(accent),
    edge = getColor(deep),
    iconBg = getColor(accent),
    iconInk = getColor(R.color.ink_strong),
    note = getColor(note),
)

/** "60 m" → 숫자는 크게(numberEmphasis), 단위는 작고 흐리게(unitColor). */
fun distanceText(label: String, unitColor: Int): CharSequence {
    val text = SpannableString(numberEmphasis(label))
    val unitStart = label.lastIndexOf(' ')
    if (unitStart >= 0) {
        text.setSpan(ForegroundColorSpan(unitColor), unitStart, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return text
}
