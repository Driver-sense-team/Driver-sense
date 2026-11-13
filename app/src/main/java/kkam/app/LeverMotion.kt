package kkam.app

/**
 * "어떻게 켜는지" 그림에서 어느 레버를 어떻게 움직이는지.
 * 현대·기아 공통 배치 기준: 왼쪽 레버 = 깜빡이·전조등, 오른쪽 레버 = 와이퍼.
 * 차종마다 다를 수 있어서 안내 문구에도 레버 위치를 함께 말한다.
 */
enum class LeverMotion(
    /** 오른쪽 레버(와이퍼)를 움직이면 true. 아니면 왼쪽 레버. */
    val rightLever: Boolean,
) {
    /** 왼쪽 레버를 아래로 끝까지: 좌회전 깜빡이. */
    LEFT_DOWN(false),

    /** 왼쪽 레버를 위로 끝까지: 우회전 깜빡이. */
    LEFT_UP(false),

    /** 왼쪽 레버를 아래로 살짝 눌렀다 놓기: 왼쪽 차로 변경 (깜빡이 세 번). */
    LEFT_TAP_DOWN(false),

    /** 왼쪽 레버를 위로 살짝 올렸다 놓기: 오른쪽 차로 변경 (깜빡이 세 번). */
    LEFT_TAP_UP(false),

    /** 왼쪽 레버 끝을 앞으로 돌리기: 전조등. */
    LEFT_TWIST(false),

    /** 오른쪽 레버를 아래로 한 칸: 와이퍼. */
    RIGHT_DOWN(true),
}

/**
 * 안내 종류와 방향으로 레버 동작을 정한다. 레버를 쓰지 않는 안내(적색 우회전 일시정지)는 null.
 * 방향(right)은 엔진 GUIDE의 action에서 온다. 안내 전에는 내비 안내의 방향을 쓴다.
 */
fun leverMotion(kind: GuideKind, right: Boolean): LeverMotion? = when (kind) {
    GuideKind.TURN_SIGNAL -> if (right) LeverMotion.LEFT_UP else LeverMotion.LEFT_DOWN
    // 유턴은 늘 왼쪽 깜빡이
    GuideKind.UTURN -> LeverMotion.LEFT_DOWN
    // 회전교차로에서 나갈 때는 오른쪽 깜빡이
    GuideKind.ROUNDABOUT -> if (right) LeverMotion.LEFT_UP else LeverMotion.LEFT_DOWN
    // 속도·브레이크는 레버가 아니라 페달이다
    GuideKind.SPEED, GuideKind.BRAKE -> null
    GuideKind.LANE_CHANGE -> if (right) LeverMotion.LEFT_TAP_UP else LeverMotion.LEFT_TAP_DOWN
    GuideKind.HEADLIGHT -> LeverMotion.LEFT_TWIST
    GuideKind.WIPER -> LeverMotion.RIGHT_DOWN
    GuideKind.RED_RIGHT_TURN -> null
    // 성에 제거·외기 순환은 레버가 아니라 공조 버튼이다. 버튼 위치가 차종마다 달라 그림은 아이콘 모양만 쓴다 (spec 13.3)
    GuideKind.DEFOG -> null
}

/**
 * 그림을 보여줄 안내인지. 켜야 할 때(주황)만, 레버를 쓰는 안내면 모두 보여준다.
 * 회전 깜빡이도 좌회전 = 아래, 우회전 = 위로 레버를 움직이는 그림을 같이 보여준다.
 * 레버가 없는 안내(적색 우회전, 김서림 버튼)는 null.
 */
fun leverFor(kind: GuideKind, right: Boolean, warn: Boolean): LeverMotion? =
    if (warn) leverMotion(kind, right) else null

/** 레버 동작을 말로. 그림과 같이 보여주고, 미리보기에서는 음성으로 읽는다. */
val LeverMotion.howTo: String
    get() = when (this) {
        LeverMotion.LEFT_DOWN -> "왼쪽 레버를 아래로 내리면 좌회전 깜빡이가 켜져요"
        LeverMotion.LEFT_UP -> "왼쪽 레버를 위로 올리면 우회전 깜빡이가 켜져요"
        LeverMotion.LEFT_TAP_DOWN -> "왼쪽 레버를 아래로 살짝 눌렀다 놓으면 깜빡이가 세 번 깜빡여요"
        LeverMotion.LEFT_TAP_UP -> "왼쪽 레버를 위로 살짝 올렸다 놓으면 깜빡이가 세 번 깜빡여요"
        LeverMotion.LEFT_TWIST -> "왼쪽 레버 끝을 앞으로 돌리면 전조등이 켜져요"
        LeverMotion.RIGHT_DOWN -> "오른쪽 레버를 아래로 한 칸 내리면 와이퍼가 움직여요"
    }
