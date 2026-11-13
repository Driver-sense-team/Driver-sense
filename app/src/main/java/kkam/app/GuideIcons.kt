package kkam.app

import kkam.engine.model.LightPhase

/** 안내 종류에 맞는 그림. 코치 카드, 팝업, 상단 알림이 같이 쓴다. 회전 깜빡이는 방향에 따라 화살표가 다르다. */
fun guideIconRes(kind: GuideKind, right: Boolean): Int = when (kind) {
    GuideKind.TURN_SIGNAL -> if (right) R.drawable.ic_turn_right else R.drawable.ic_turn_left
    GuideKind.UTURN -> R.drawable.ic_uturn
    GuideKind.ROUNDABOUT -> R.drawable.ic_roundabout
    GuideKind.SPEED -> R.drawable.ic_speed
    GuideKind.BRAKE -> R.drawable.ic_brake
    GuideKind.LANE_CHANGE -> R.drawable.ic_lane_change
    GuideKind.HEADLIGHT -> R.drawable.ic_headlight
    GuideKind.WIPER -> R.drawable.ic_wiper
    GuideKind.RED_RIGHT_TURN -> R.drawable.ic_red_light
    GuideKind.DEFOG -> R.drawable.ic_defog
}

/**
 * 코치 카드·팝업의 큰 그림. 적색 우회전은 실제 신호등처럼 켜진 칸에 색을 칠한 그림을 쓴다 ([isMultiColor]).
 * 신호를 모르면 안내 기준인 빨간불로 그린다 (R3 안내는 적색일 때만 나온다).
 */
fun cardIconRes(kind: GuideKind, right: Boolean, phase: LightPhase?): Int =
    if (kind == GuideKind.RED_RIGHT_TURN) trafficLightRes(phase ?: LightPhase.RED) else guideIconRes(kind, right)

/** 켜진 칸만 색이 있는 신호등 그림. */
fun trafficLightRes(phase: LightPhase?): Int = when (phase) {
    LightPhase.RED -> R.drawable.ic_traffic_red
    LightPhase.YELLOW -> R.drawable.ic_traffic_yellow
    LightPhase.GREEN -> R.drawable.ic_traffic_green
    LightPhase.UNKNOWN, null -> R.drawable.ic_traffic_off
}

/** 그림 자체에 색이 들어 있어서 tint를 입히면 안 되는 안내. */
fun GuideKind.isMultiColor(): Boolean = this == GuideKind.RED_RIGHT_TURN

/**
 * 오른쪽 상태 칩의 작은 그림. 깜빡이 안내는 깜빡이 화살표(켜짐 = 꽉 참, 꺼짐 = 빔),
 * 적색 우회전은 지금 신호 색의 신호등, 그 밖(전조등·와이퍼·김서림)은 그 기능 그림.
 */
fun chipIconRes(kind: GuideKind, on: Boolean, phase: LightPhase?): Int = when (kind) {
    GuideKind.TURN_SIGNAL, GuideKind.UTURN, GuideKind.ROUNDABOUT, GuideKind.LANE_CHANGE -> if (on) R.drawable.ic_blinker_on else R.drawable.ic_blinker_off
    GuideKind.RED_RIGHT_TURN -> trafficLightRes(phase ?: LightPhase.RED)
    else -> guideIconRes(kind, right = false)
}
