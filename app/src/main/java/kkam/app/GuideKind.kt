package kkam.app

import kkam.engine.model.RuleId

/**
 * 주행 중 안내의 종류. 카드·팝업·상단 알림이 종류에 맞는 그림과 제목을 고른다.
 * 엔진 룰과의 연결은 [guideKind]. 차선 변경 그림은 나들목(R4)과 경로 데모의 차선 변경 팝업이 같이 쓴다.
 */
enum class GuideKind(
    /** 미리보기와 기록에 쓰는 이름. */
    val label: String,
) {
    TURN_SIGNAL("회전 깜빡이"),
    LANE_CHANGE("차선 변경"),
    HEADLIGHT("전조등"),
    WIPER("와이퍼"),
    RED_RIGHT_TURN("적색 우회전"),
    DEFOG("김서림"),
}

/**
 * 엔진 룰 → 안내 종류. 음성 안내가 없는 룰(R2: 깜빡이 없이 회전, 기록만)은 null.
 * R4(나들목 깜빡이)는 차선을 바꿔 빠지는 안내라서 차선 변경 그림을 쓴다.
 */
fun RuleId.guideKind(): GuideKind? = when (this) {
    RuleId.R1 -> GuideKind.TURN_SIGNAL
    RuleId.R2 -> null
    RuleId.R3 -> GuideKind.RED_RIGHT_TURN
    RuleId.R4 -> GuideKind.LANE_CHANGE
    RuleId.R5 -> GuideKind.HEADLIGHT
    RuleId.R6 -> GuideKind.WIPER
    RuleId.R7 -> GuideKind.DEFOG
}
