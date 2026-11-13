package kkam.app

import kkam.engine.model.Guide
import kkam.engine.model.RuleId
import kkam.engine.model.TurnType

/**
 * 주행 중 안내의 종류. 카드·팝업·상단 알림이 종류에 맞는 그림과 제목을 고른다.
 * 엔진 룰과의 연결은 [guideKind]. 차선 변경 그림은 나들목(R4)과 경로 데모의 차선 변경 팝업이 같이 쓴다.
 */
enum class GuideKind(
    /** 미리보기와 기록에 쓰는 이름. */
    val label: String,
) {
    TURN_SIGNAL("회전 깜빡이"),

    /** 유턴 전 왼쪽 깜빡이 (R1이 UTURN 안내에서 낸다, spec 3장). 그림만 유턴 화살표이고 나머지는 회전 깜빡이와 같다. */
    UTURN("유턴 깜빡이"),

    /** 회전교차로에 들어갈 때 왼쪽, 나갈 때 오른쪽 깜빡이 (R8, spec 14장). 방향은 [right]로 나눈다. */
    ROUNDABOUT("회전교차로"),

    /** 속도를 줄이라는 안내 (경로 데모 각본: 커브, 진출로). 엔진 룰 없음. */
    SPEED("속도"),

    /** 브레이크 안내 (경로 데모 각본: 급정거, 브레이크 타이밍). 엔진 룰 없음. */
    BRAKE("브레이크"),
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
    RuleId.R8 -> GuideKind.ROUNDABOUT
}

/** 음성 안내 하나의 종류. R1은 내비 안내가 유턴이면 유턴 깜빡이, 그 밖은 [RuleId.guideKind]. */
fun Guide.guideKind(turn: TurnType?): GuideKind? =
    if (rule == RuleId.R1 && turn == TurnType.UTURN) GuideKind.UTURN else rule.guideKind()
