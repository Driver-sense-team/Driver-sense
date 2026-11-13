package kkam.app

import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * 주행 화면 (기획서 UI-1, screen_drive.xml). 코치 카드, 한 줄 문구, 오늘 성적.
 * 터치할 것은 두지 않는다 (주행 중 터치 조작 없음, 기획서 5.4). 값은 모두 [CoachState]에서 온다.
 *
 * 카드는 흰 바탕에 검정 글자다. 상태 색([CardLook])은 칠할 수 있는 곳에 모두 쓴다.
 *
 * | 모습 | 테두리 | 방향 그림 | 행동 알약 | 깜빡이 칩 | 카드 밑 문구 |
 * | --- | --- | --- | --- | --- | --- |
 * | IDLE (대기) | 연회색 | 연회색 원 + 경로 화살표 | 숨김 | 숨김 | 검정 글자 |
 * | WARN (켜세요) | 노랑 | 노랑 원 + 방향 | 노랑 "! 지금 켜세요" | 꺼짐: 노랑 테두리 + 빈 화살표 | 노랑 |
 * | OK (좋아요) | 파랑 | 파랑 원 + 방향 | 파랑 "✓ 좋아요" | 켜짐: 파랑으로 채움 + 꽉 찬 화살표 | 파랑 |
 * | MISS (놓침) | 빨강 | 빨강 원 + 방향 | 빨강 "X 다음엔 미리 켜요" | 꺼짐: 빨강 테두리 + 빈 화살표 | 빨강 |
 *
 * 적색 우회전은 신호등 그림의 켜진 칸을 지금 신호 색(빨강·노랑·초록)으로 칠하고, 칩에도 같은 신호등을 쓴다.
 * 켜야 할 때(주황)는 어떻게 하는지도 보여준다: 레버를 쓰는 안내는 레버 그림, 김서림은 누를 공조 버튼 그림.
 * 오늘 성적 링: 지킨 몫은 파랑, 놓친 몫은 빨강, 판정이 없으면 연회색.
 * 모습이 바뀔 때만 색을 [FADE_MS] 동안 한 번 바꾼다. 반복 애니메이션은 없다 (레버 그림 제외: 켜는 방법 안내).
 */
class DriveScreen(private val activity: Activity) {
    private val root: View = activity.findViewById(R.id.driveScreen)
    private val card: LinearLayout = activity.findViewById(R.id.card)
    private val turnIcon: ImageView = activity.findViewById(R.id.turnIcon)
    private val lever: LeverGuideView = activity.findViewById(R.id.lever)
    private val turn: TextView = activity.findViewById(R.id.turn)
    private val distance: TextView = activity.findViewById(R.id.distance)
    private val idleHint: TextView = activity.findViewById(R.id.idleHint)
    private val actionRow: View = activity.findViewById(R.id.actionRow)
    private val badgeBox: View = activity.findViewById(R.id.badgeBox)
    private val badgeIcon: ImageView = activity.findViewById(R.id.badgeIcon)
    private val badge: TextView = activity.findViewById(R.id.badge)
    private val signalBox: View = activity.findViewById(R.id.signalBox)
    private val signalIcon: ImageView = activity.findViewById(R.id.signalIcon)
    private val signal: TextView = activity.findViewById(R.id.signal)
    private val message: TextView = activity.findViewById(R.id.message)
    private val scoreRing: ProgressBar = activity.findViewById(R.id.scoreRing)
    private val scoreRate: TextView = activity.findViewById(R.id.scoreRate)
    private val todayKept: TextView = activity.findViewById(R.id.todayKept)
    private val todayTotal: TextView = activity.findViewById(R.id.todayTotal)
    private val buttonGuide = ButtonGuide(activity.findViewById(R.id.buttonGuide), BUTTON_ICON_DP, R.style.Kk_Body)

    private val ink = color(R.color.ink_strong)
    private val inkSoft = color(R.color.ink_soft)
    private val white = color(R.color.card_light)

    // 배경은 한 번만 만들고 색만 바꾼다 (애니메이션 중 매 프레임 새로 만들지 않게).
    private val cardFill = activity.rounded(white, radiusDp = CARD_RADIUS_DP)
    private val iconCircle = activity.pillBackground(white)
    private val badgeFill = activity.pillBackground(white)
    private val signalFill = activity.pillBackground(white)
    /** 성적 링의 바탕 고리(지키지 못한 몫). 놓친 판정이 있으면 빨강. */
    private val ringTrack = (scoreRing.progressDrawable.mutate() as LayerDrawable)
        .findDrawableByLayerId(android.R.id.background) as GradientDrawable

    /** 지금 칠해진 색. 모습이 바뀌면 여기서 새 색까지 페이드한다. */
    private var painted: CardPalette? = null
    private var paintedLook: CardLook? = null
    private var fade: ValueAnimator? = null
    /** 지금 그림이 여러 색(신호등)이라 tint를 입히지 않는가. */
    private var multiColor = false

    init {
        card.background = cardFill
        turnIcon.background = iconCircle
        badgeBox.background = badgeFill
        signalBox.background = signalFill
        activity.findViewById<View>(R.id.scoreCard).background =
            activity.rounded(white, radiusDp = CARD_RADIUS_DP, strokeColor = color(R.color.card_light_line))
        // 고정 색: 흰 카드 위 글자는 검정, 알약 글자·그림도 상태 색 위 검정.
        distance.setTextColor(ink)
        badge.setTextColor(ink)
        badgeIcon.imageTintList = ColorStateList.valueOf(ink)
    }

    fun show(screen: Screen) {
        root.visibility = if (screen == Screen.DRIVE) View.VISIBLE else View.GONE
    }

    fun render(state: CoachState) {
        val look = state.look()
        renderCard(state, look)
        message.text = state.message
        renderScore(state)
        paint(state, look)
    }

    /** 카드 안 글자와 그림. 색은 [paint]가 칠한다. */
    private fun renderCard(state: CoachState, look: CardLook) {
        val active = look != CardLook.IDLE
        // 그림: 안내 중이면 안내 종류에 맞는 그림(회전이면 방향 화살표), 대기 중이면 경로 화살표.
        // 적색 우회전은 지금 신호 색이 켜진 신호등 그림.
        turnIcon.setImageResource(
            if (active) cardIconRes(state.guideKind, state.right, state.lightPhase) else R.drawable.ic_route_idle,
        )
        multiColor = active && state.guideKind.isMultiColor()
        // 대기 중에는 그림을 작게 해서 차분하게, 안내 중에는 원 안을 크게 채운다.
        val iconPadding = activity.dp(if (active) ACTIVE_ICON_PADDING_DP else IDLE_ICON_PADDING_DP)
        turnIcon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)

        turn.text = state.turnLabel
        distance.text = distanceText(state.distanceLabel, inkSoft)
        // 거리가 없는 안내(예: 와이퍼)는 거리 줄을 접어서 제목이 그림 옆 가운데에 오게 한다.
        val showDistance = active && state.distanceLabel.isNotEmpty()
        distance.visibility = if (showDistance) View.VISIBLE else View.GONE
        idleHint.visibility = if (active) View.GONE else View.VISIBLE
        // 거리가 주인공일 때만 제목을 흐리게. 대기 중이나 거리가 없는 안내(김서림·와이퍼)는 제목이 주인공이라 검정.
        turn.setTextColor(if (showDistance) inkSoft else ink)

        actionRow.visibility = if (active) View.VISIBLE else View.GONE
        badge.text = state.badge
        badgeIcon.setImageResource(look.badgeIconRes())
        signal.text = state.signalLabel
        signalBox.visibility = if (state.signalLabel.isEmpty()) View.GONE else View.VISIBLE
    }

    /** 오늘 성적: 링(지킨 비율) + "지킴 n / 전체 m번". 판정이 없으면 퍼센트 대신 "–". */
    private fun renderScore(state: CoachState) {
        val rate = if (state.total == 0) null else state.kept * 100 / state.total
        scoreRing.progress = rate ?: 0
        // 링의 나머지(놓친 몫)는 놓친 판정이 있을 때만 빨강.
        ringTrack.setColor(color(if (state.kept < state.total) R.color.miss else R.color.card_light_line))
        scoreRate.text = rate?.let { activity.getString(R.string.drive_today_rate, it) }
            ?: activity.getString(R.string.drive_today_none)
        todayKept.text = state.kept.toString()
        todayTotal.text = activity.getString(R.string.drive_today_total, state.total)
    }

    /** 모습에 맞는 색을 칠한다. 모습이 바뀐 때만 [FADE_MS] 동안 페이드, 그 밖에는 바로 칠한다. */
    private fun paint(state: CoachState, look: CardLook) {
        val target = activity.palette(look)
        val from = painted
        val lookChanged = paintedLook != null && paintedLook != look
        paintedLook = look
        // 상태 칩: 켜짐(파랑)은 상태 색으로 채우고, 꺼짐은 흰 바탕에 상태 색 테두리.
        // 그림은 안내 종류에 맞게 (깜빡이 화살표 꽉 참/빔, 신호등, 전조등, 김서림).
        val signalOn = look == CardLook.OK
        signalIcon.setImageResource(chipIconRes(state.guideKind, signalOn, state.lightPhase))
        // 레버 그림은 프레임마다 안내 종류가 바뀔 수 있어서 늘 다시 넘긴다.
        val leverMotion = leverFor(state.guideKind, state.right, look == CardLook.WARN)

        val kind = state.guideKind
        val warn = look == CardLook.WARN

        fade?.cancel()
        if (from == null || !lookChanged || root.visibility != View.VISIBLE) {
            apply(target, signalOn, leverMotion, kind, warn)
            return
        }
        fade = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FADE_MS
            addUpdateListener { apply(from.mix(target, it.animatedFraction), signalOn, leverMotion, kind, warn) }
            start()
        }
    }

    private fun apply(palette: CardPalette, signalOn: Boolean, leverMotion: LeverMotion?, kind: GuideKind, warn: Boolean) {
        painted = palette
        cardFill.setStroke(activity.dp(CARD_EDGE_DP), palette.edge)
        iconCircle.setColor(palette.iconBg)
        turnIcon.imageTintList = if (multiColor) null else ColorStateList.valueOf(palette.iconInk)
        badgeFill.setColor(palette.accent)
        signalFill.setColor(if (signalOn) palette.accent else white)
        signalFill.setStroke(activity.dp(SIGNAL_EDGE_DP), palette.edge)
        signal.setTextColor(ink)
        signalIcon.imageTintList = when {
            multiColor -> null
            signalOn -> ColorStateList.valueOf(ink)
            else -> ColorStateList.valueOf(palette.edge)
        }
        message.setTextColor(palette.note)
        lever.show(leverMotion, palette.edge)
        buttonGuide.show(kind, warn, palette.accent, palette.edge)
    }

    private fun color(id: Int) = activity.getColor(id)

    private companion object {
        const val FADE_MS = 200L
        const val CARD_RADIUS_DP = 32
        /** 상태를 테두리로 보여주므로 굵게. */
        const val CARD_EDGE_DP = 8
        const val SIGNAL_EDGE_DP = 3
        const val ACTIVE_ICON_PADDING_DP = 32
        const val IDLE_ICON_PADDING_DP = 52
        /** 김서림 버튼 그림 한 칸의 크기. */
        const val BUTTON_ICON_DP = 176
    }
}
