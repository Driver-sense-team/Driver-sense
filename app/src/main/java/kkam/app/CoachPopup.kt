package kkam.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager

/**
 * 내비 위에 뜨는 작은 안내 카드 (겹침 창). 서비스가 하나만 갖고 [show]로 갱신한다.
 * "다른 앱 위에 표시" 권한이 없으면 아무것도 띄우지 않는다. 그때는 서비스가 [CoachHeadsUp]으로 바꾼다.
 */
class CoachPopup(context: Context) : CoachNotifier {
    private val context = context.applicationContext
    private val windowManager = context.getSystemService(WindowManager::class.java)
    /** 겹침 창에 붙는 뷰. 그림자가 잘리지 않게 카드 둘레에 여백을 둔 틀이다. */
    private val window: View = LayoutInflater.from(this.context).inflate(R.layout.popup_card, null)
    /** 카드 그리기는 지도 위 팝업([MapPopupCard])과 같은 코드를 쓴다. */
    private val painter = PopupCardPainter(window.findViewById(R.id.popupCard))
    private var attached = false
    private var shown: Popup? = null
    /** 지금 칠해진 색. 주황 → 파랑처럼 모습이 바뀔 때 한 번만 페이드한다. */
    private var painted: CardPalette? = null
    private var fade: ValueAnimator? = null

    /** popup이 null이면 숨긴다. area는 앱 칸(내비 자리)의 크기. 모르면 화면 가운데. */
    override fun show(popup: Popup?, area: Rect?) {
        if (popup == shown) return
        shown = popup
        if (popup == null) {
            hide()
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "overlay permission missing")
            return
        }
        paint(painter.fill(popup))
        if (!attached) {
            runCatching { windowManager.addView(window, layoutParams(area)) }
                .onSuccess { attached = true }
                .onFailure { Log.w(TAG, "overlay failed", it) }
            window.alpha = 0f
            window.animate().alpha(1f).setDuration(FADE_MS).start()
        }
    }

    override fun release() {
        shown = null
        hide()
    }

    private fun hide() {
        fade?.cancel()
        if (!attached) return
        runCatching { windowManager.removeView(window) }
        attached = false
    }

    /** 색. 이미 떠 있는 카드의 모습이 바뀔 때(켜세요 → 좋아요)만 [FADE_MS] 동안 한 번 페이드한다. */
    private fun paint(target: CardPalette) {
        val from = painted
        fade?.cancel()
        if (from == null || from == target || !attached) {
            apply(target)
            return
        }
        fade = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FADE_MS
            addUpdateListener { apply(from.mix(target, it.animatedFraction)) }
            start()
        }
    }

    private fun apply(palette: CardPalette) {
        painted = palette
        painter.paint(palette)
    }

    /**
     * 앱 칸 위쪽 가운데. 내비 버튼(오른쪽 위 2D, 아래쪽 검색·음량)을 피한다.
     * 카드 밖 터치는 내비로 간다. 카드 안 터치는 막는다: 터치를 통과시키는 겹침 창은 시스템이 반투명하게 그려
     * 운전 중 색이 흐려지기 때문이다. 카드는 작고 몇 초면 사라진다.
     */
    private fun layoutParams(area: Rect?) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // 겹침 창은 화면 전체 기준으로 놓인다. 서비스의 displayMetrics는 앱 칸 크기라서 화면 전체 폭을 따로 잰다.
        val screenWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds.width()
        } else {
            context.resources.displayMetrics.widthPixels
        }
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        // CENTER_HORIZONTAL에서 x는 화면 가운데로부터의 거리다. 앱 칸 가운데로 옮긴다.
        // Pleos 화면에서 앱은 자기 위치를 (0, 0)으로만 알 수 있어서 폭만 쓴다. 앱 칸은 화면 오른쪽에 붙어 있다
        // (에뮬레이터: 왼쪽 853dp는 차량 화면, 앱 칸 853~2535). 오른쪽 끝 시스템 손잡이(약 25dp)만큼 오차가 있다.
        x = area?.let { (screenWidth - it.width()) / 2 } ?: 0
        // 위 여백은 popup_card.xml 바깥 틀의 여백(그림자 자리)이 대신한다.
        y = area?.top ?: 0
    }

    private companion object {
        const val TAG = "KkamPopup"
        const val FADE_MS = 200L
    }
}
