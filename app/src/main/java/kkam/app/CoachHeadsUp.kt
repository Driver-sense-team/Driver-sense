package kkam.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.util.Log

/**
 * 차량 상단 알림으로 안내한다 ([CoachPopup]을 쓸 수 없을 때의 대비책).
 * 같은 회전 안내는 알림 하나를 고쳐 쓴다. 주황 → 파랑으로 바뀌어도 소리·진동을 다시 내지 않는다.
 * 모양(흰 바탕, 작은 글씨)은 시스템이 정해서 바꿀 수 없다. 글자와 강조색만 넣는다.
 */
class CoachHeadsUp(context: Context) : CoachNotifier {
    private val context = context.applicationContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var shown: Popup? = null

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.headsup_channel), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    override fun show(popup: Popup?, area: Rect?) {
        if (popup == shown) return
        shown = popup
        if (popup == null) {
            manager.cancel(NOTICE_ID)
            return
        }
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "notifications disabled")
            return
        }
        manager.notify(NOTICE_ID, build(popup))
    }

    override fun release() {
        shown = null
        manager.cancel(NOTICE_ID)
    }

    private fun build(popup: Popup): Notification {
        val accent = context.getColor(
            when (popup.tone) {
                PopupTone.OK -> R.color.coach_ok
                PopupTone.WARN -> R.color.coach_warn
                PopupTone.MISS -> R.color.coach_miss
            },
        )
        val iconRes = guideIconRes(popup.kind, popup.right)
        val arrow = Icon.createWithResource(context, iconRes).setTint(accent)
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(iconRes)
            .setLargeIcon(arrow)
            .setColor(accent)
            .setColorized(true)
            .setContentTitle(listOf(popup.turnLabel, popup.distanceLabel).filter { it.isNotEmpty() }.joinToString(" "))
            .setContentText(popup.headline)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val TAG = "KkamHeadsUp"
        const val CHANNEL = "coach_guide"
        const val NOTICE_ID = 1
    }
}
