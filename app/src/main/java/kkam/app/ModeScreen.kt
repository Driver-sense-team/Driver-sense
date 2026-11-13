package kkam.app

import android.app.Activity
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * 운전 코치 켬/끔 화면. 기어 P일 때만 보인다.
 * 어느 화면을 보일지는 MainActivity가 [Screen]으로 정하고, 이 클래스는 자기 화면만 그린다.
 *
 * 켬/끔은 [ModeStore]에 바로 저장한다. 주행 세션이 프레임마다 거기서 읽는다.
 * 오늘 기록 수는 화면을 열 때마다 [RecordStore]에서 읽는다 (세션과 같은 저장소라 따로 넘겨받지 않는다).
 */
class ModeScreen(
    private val activity: Activity,
    private val modes: ModeStore = ModeStore(activity),
    private val records: RecordStore = RecordStore(activity),
) {
    private val root: View = activity.findViewById(R.id.modeScreen)
    private val card: View = activity.findViewById(R.id.modeCard)
    private val icon: ImageView = activity.findViewById(R.id.modeIcon)
    private val title: TextView = activity.findViewById(R.id.modeTitle)
    private val desc: TextView = activity.findViewById(R.id.modeDesc)
    private val detail: TextView = activity.findViewById(R.id.modeDetail)
    private val track: View = activity.findViewById(R.id.modeTrack)
    private val thumb: TextView = activity.findViewById(R.id.modeThumb)
    private val recordsLine: TextView = activity.findViewById(R.id.modeRecords)

    init {
        card.setOnClickListener { toggle() }
        draw(modes.load())
    }

    fun show(screen: Screen) {
        val visible = screen == Screen.MODE
        root.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            draw(modes.load())
            drawRecords(records.load().size)
        }
    }

    private fun toggle() {
        val next = if (modes.isCoachOn()) DriveMode.OFF else DriveMode.ON
        modes.save(next)
        draw(next)
    }

    /** 켬: 파란 테두리 + 옅은 파란 배경 + 파란 스위치(오른쪽). 끔: 기본 카드 + 회색 스위치(왼쪽). */
    private fun draw(mode: DriveMode) {
        val on = mode == DriveMode.ON
        val ok = activity.getColor(R.color.ok)
        val line = activity.getColor(R.color.line)
        card.isSelected = on
        card.background = if (on) {
            activity.rounded(activity.getColor(R.color.ok_soft), CARD_RADIUS_DP, strokeColor = ok, strokeDp = ON_STROKE_DP)
        } else {
            activity.rounded(activity.getColor(R.color.surface), CARD_RADIUS_DP, strokeColor = line)
        }
        icon.background = activity.rounded(activity.getColor(if (on) R.color.ok else R.color.surface_2), CARD_RADIUS_DP)
        icon.imageTintList = ColorStateList.valueOf(activity.getColor(if (on) R.color.on_accent else R.color.muted))

        title.setText(if (on) R.string.mode_on_title else R.string.mode_off_title)
        title.setTextColor(activity.getColor(if (on) R.color.text else R.color.muted))
        desc.setText(if (on) R.string.mode_on_desc else R.string.mode_off_desc)
        detail.setText(if (on) R.string.mode_on_detail else R.string.mode_off_detail)

        track.background = if (on) activity.pillBackground(ok) else activity.pillBackground(activity.getColor(R.color.surface_2), strokeColor = line)
        thumb.setText(if (on) R.string.mode_switch_on else R.string.mode_switch_off)
        thumb.setTextColor(activity.getColor(if (on) R.color.ok else R.color.muted))
        // 손잡이는 늘 흰 동그라미(surface). 켬이면 파란 트랙 위에 파란 글자, 끔이면 회색 테두리에 회색 글자.
        thumb.background = activity.pillBackground(activity.getColor(R.color.surface), strokeColor = if (on) null else line)
        (thumb.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER_VERTICAL or (if (on) Gravity.END else Gravity.START)
        thumb.requestLayout()
        card.contentDescription = activity.getString(if (on) R.string.mode_on_title else R.string.mode_off_title)
    }

    private fun drawRecords(count: Int) {
        recordsLine.text = if (count > 0) {
            activity.getString(R.string.mode_records_saved, count)
        } else {
            activity.getString(R.string.mode_records_empty)
        }
        recordsLine.setTextColor(activity.getColor(if (count > 0) R.color.text else R.color.muted))
    }

    private companion object {
        const val CARD_RADIUS_DP = 28
        const val ON_STROKE_DP = 6
    }
}
