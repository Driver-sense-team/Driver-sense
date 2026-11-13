package kkam.app

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout

/**
 * 경로 데모 지도 위쪽 가운데에 뜨는 안내 카드. 내비 위 팝업([CoachPopup])과 같은 popup_card.xml을 쓰고,
 * 같은 [PopupCardPainter]로 그려서 모습이 똑같다 (흰 바탕 + 상태 색 테두리).
 * 다른 점: 겹침 창이 아니라 지도 칸(routeMapPanel) 안의 보통 뷰라서 권한이 필요 없고 우리 앱 화면에서만 보인다.
 * 주황 "지금 켜세요" → 파랑 "좋아요", 판정이 나오거나 회전을 지나면 숨는다 (규칙은 PopupPolicy.kt).
 * 누를 것은 없다 (주행 화면).
 */
class MapPopupCard(parent: FrameLayout) {
    /** 그림자가 잘리지 않게 카드 둘레에 여백을 둔 틀. 위 여백도 이 틀의 여백이 대신한다. */
    private val frame: View = LayoutInflater.from(parent.context).inflate(R.layout.popup_card, parent, false)
    private val painter = PopupCardPainter(frame.findViewById(R.id.popupCard))
    private var shown: Popup? = null

    init {
        frame.visibility = View.GONE
        parent.addView(
            frame,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            },
        )
    }

    /** null이면 숨긴다. 같은 값이면 다시 그리지 않는다. */
    fun show(popup: Popup?) {
        if (popup == shown) return
        shown = popup
        if (popup == null) {
            frame.visibility = View.GONE
            return
        }
        painter.paint(painter.fill(popup))
        frame.visibility = View.VISIBLE
    }
}
