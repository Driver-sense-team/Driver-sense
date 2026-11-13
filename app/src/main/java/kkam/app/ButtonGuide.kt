package kkam.app

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * "어떤 버튼을 누르는지" 그림. 레버가 아니라 공조 버튼을 누르는 안내(김서림)에 쓴다. [LeverGuideView]의 버튼판.
 * 차 공조 버튼처럼 둥근 네모 두 개: ① 앞유리 성에 제거, ② 외기 순환. 번호는 누를 순서다.
 * 버튼 위치는 차종마다 달라서 위치 대신 버튼에 그려진 모양을 보여준다 (spec 13.3).
 * 움직이지 않는다. 상태가 바뀔 때만 색이 바뀐다.
 *
 * @param box 버튼을 담을 빈 가로 LinearLayout. 코치 카드(크게)와 팝업(작게)이 크기만 달리 쓴다.
 */
class ButtonGuide(private val box: LinearLayout, iconDp: Int, labelStyle: Int) {
    private val context = box.context
    private val tiles = listOf(
        tile(R.drawable.ic_defog, R.string.drive_button_defrost, iconDp, labelStyle),
        tile(R.drawable.ic_air_outside, R.string.drive_button_fresh, iconDp, labelStyle),
    )

    /** 김서림을 켜야 할 때(주황)만 보인다. accent = 버튼 채움, edge = 버튼 테두리. */
    fun show(kind: GuideKind, warn: Boolean, accent: Int, edge: Int) {
        box.visibility = if (warn && kind == GuideKind.DEFOG) View.VISIBLE else View.GONE
        for (tile in tiles) {
            tile.fill.setColor(accent)
            tile.fill.setStroke(context.dp(TILE_EDGE_DP), edge)
        }
    }

    private fun tile(icon: Int, label: Int, iconDp: Int, labelStyle: Int): Tile {
        val ink = context.getColor(R.color.ink_strong)
        val fill = context.rounded(ink, radiusDp = TILE_RADIUS_DP)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        // 버튼 모양: 둥근 네모 안에 버튼 그림.
        val button = ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(ink)
            background = fill
            val pad = iconDp / 5
            setPadding(context.dp(pad), context.dp(pad), context.dp(pad), context.dp(pad))
        }
        column.addView(button, LinearLayout.LayoutParams(context.dp(iconDp), context.dp(iconDp)))
        val name = TextView(context, null, 0, labelStyle).apply {
            setText(label)
            setTextColor(ink)
            maxLines = 1
        }
        column.addView(name, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(LABEL_GAP_DP)
        })
        box.addView(column, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            if (box.childCount > 0) marginStart = context.dp(TILE_GAP_DP)
        })
        return Tile(fill)
    }

    private class Tile(val fill: GradientDrawable)

    private companion object {
        const val TILE_RADIUS_DP = 20
        const val TILE_EDGE_DP = 4
        const val TILE_GAP_DP = 24
        const val LABEL_GAP_DP = 8
    }
}
