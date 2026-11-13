package kkam.app

import android.graphics.Rect

/**
 * 내비가 앞에 있을 때 안내를 보여주는 방법.
 * - [CoachPopup]: 겹침 창 카드. 우리 디자인 그대로. "다른 앱 위에 표시" 권한이 필요하다.
 * - [CoachHeadsUp]: 차량 상단 알림. 공식 기능이라 특별 권한이 없지만 모양은 시스템 알림 틀.
 * 어느 쪽을 쓸지는 [CoachService]가 권한을 보고 정한다.
 */
interface CoachNotifier {
    /** popup이 null이면 숨긴다. area는 앱 칸 크기 (모르면 null). */
    fun show(popup: Popup?, area: Rect?)

    fun release()
}
