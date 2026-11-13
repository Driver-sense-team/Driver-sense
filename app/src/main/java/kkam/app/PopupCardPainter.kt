package kkam.app

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView

/**
 * popup_card.xml 카드 한 장을 그리는 공통 코드.
 * 내비 위 팝업([CoachPopup])과 경로 데모 지도 위 팝업([MapPopupCard])이 같이 쓴다. 그래서 두 카드는 늘 같은 모습이다:
 * 흰 바탕 + 굵은 상태 색 테두리, 상태 색 원 안의 검정 방향 그림, 상태 색 알약에 검정 글자. 색 규칙은 CoachLook.kt.
 *
 * 쓰는 순서: [fill]로 글자·그림을 채우고 돌려받은 색을 [paint]로 칠한다.
 * 둘을 나눈 이유는 색을 바로 칠할지 페이드할지를 부르는 쪽이 정하기 때문이다.
 */
class PopupCardPainter(card: View) {
    private val context = card.context
    private val density = context.resources.displayMetrics.density
    private val icon: ImageView = card.findViewById(R.id.popupIcon)
    private val turn: TextView = card.findViewById(R.id.popupTurn)
    private val distance: TextView = card.findViewById(R.id.popupDistance)
    private val badgeIcon: ImageView = card.findViewById(R.id.popupBadgeIcon)
    private val headline: TextView = card.findViewById(R.id.popupHeadline)
    private val lever: LeverGuideView = card.findViewById(R.id.popupLever)
    private val buttonGuide = ButtonGuide(card.findViewById(R.id.popupButtonGuide), BUTTON_ICON_DP, R.style.Kk_Caption)
    private val ink = context.getColor(R.color.ink_strong)
    private val inkSoft = context.getColor(R.color.ink_soft)

    // 배경은 한 번만 만들고 색만 바꾼다.
    // 카드 = 흰색 95% 바탕 + 굵은 상태 색 테두리, 그림 원·알약 = 상태 색.
    private val cardFill = GradientDrawable().apply {
        setColor(context.getColor(R.color.popup_light))
        cornerRadius = CARD_RADIUS_DP * density
    }
    private val iconCircle = GradientDrawable().apply { cornerRadius = PILL_RADIUS_DP * density }
    private val badgeFill = GradientDrawable().apply { cornerRadius = PILL_RADIUS_DP * density }
    /** 지금 그림이 여러 색(신호등)이라 tint를 입히지 않는가. */
    private var multiColor = false

    init {
        card.background = cardFill
        icon.background = iconCircle
        card.findViewById<View>(R.id.popupBadge).background = badgeFill
        badgeIcon.imageTintList = ColorStateList.valueOf(ink)
    }

    /** 글자와 그림을 채운다. 주행 화면 코치 카드와 같은 순서: 방향 그림 → 회전·거리 → 행동 알약. 이 팝업에 칠할 색을 돌려준다. */
    fun fill(popup: Popup): CardPalette {
        val look = popup.tone.look()
        // 적색 우회전은 빨간불이 켜진 신호등 (R3 안내는 적색일 때만 나온다).
        icon.setImageResource(cardIconRes(popup.kind, popup.right, phase = null))
        multiColor = popup.kind.isMultiColor()
        turn.text = popup.turnLabel
        distance.text = distanceText(popup.distanceLabel, inkSoft)
        distance.visibility = if (popup.distanceLabel.isEmpty()) View.GONE else View.VISIBLE
        // 거리가 없는 안내(김서림·와이퍼·차선 변경)는 제목이 주인공이라 검정.
        turn.setTextColor(if (popup.distanceLabel.isEmpty()) ink else inkSoft)
        headline.text = popup.headline
        badgeIcon.setImageResource(look.badgeIconRes())
        val palette = context.palette(look)
        lever.show(leverFor(popup.kind, popup.right, look == CardLook.WARN), palette.edge)
        buttonGuide.show(popup.kind, look == CardLook.WARN, palette.accent, palette.edge)
        return palette
    }

    /** 색을 칠한다: 카드 테두리, 방향 그림 원과 그림, 행동 알약. */
    fun paint(palette: CardPalette) {
        cardFill.setStroke((CARD_EDGE_DP * density).toInt(), palette.edge)
        iconCircle.setColor(palette.iconBg)
        icon.imageTintList = if (multiColor) null else ColorStateList.valueOf(palette.iconInk)
        badgeFill.setColor(palette.accent)
    }

    private companion object {
        const val CARD_RADIUS_DP = 32
        /** 상태를 테두리로 보여주므로 굵게. */
        const val CARD_EDGE_DP = 6
        const val PILL_RADIUS_DP = 999
        /** 김서림 버튼 그림 한 칸의 크기. 팝업 높이에 맞춰 작게. */
        const val BUTTON_ICON_DP = 88
    }
}
