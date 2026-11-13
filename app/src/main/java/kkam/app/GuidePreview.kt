package kkam.app

/**
 * 데모 패널의 "안내 종류 미리보기"에 쓰는 예시 값. 엔진 출력이 아니라 화면 확인용 문구다.
 * 시연에서 안내 종류마다 그림과 배치를 바로 보여 주려고 둔다.
 */
fun CoachState.preview(kind: GuideKind): CoachState {
    val (title, distance, status, headline) = when (kind) {
        GuideKind.TURN_SIGNAL -> listOf("좌회전", "60 m", "깜빡이 꺼짐", "지금 켜세요")
        GuideKind.UTURN -> listOf("유턴", "60 m", "깜빡이 꺼짐", "왼쪽 깜빡이 켜세요")
        GuideKind.ROUNDABOUT -> listOf("회전교차로 나갈 때", "40 m", "깜빡이 꺼짐", "오른쪽 깜빡이 켜세요")
        GuideKind.SPEED -> listOf("커브길", "", "80 km/h", "60까지 줄이세요")
        GuideKind.BRAKE -> listOf("급정거", "", "앞차 가까움", "앞차와 거리를 두세요")
        GuideKind.LANE_CHANGE -> listOf("오른쪽 차로로", "300 m", "깜빡이 꺼짐", "깜빡이 켜고 옮기세요")
        GuideKind.HEADLIGHT -> listOf("터널 진입", "200 m", "전조등 꺼짐", "전조등 켜세요")
        GuideKind.WIPER -> listOf("비가 와요", "", "와이퍼 꺼짐", "와이퍼 켜세요")
        GuideKind.RED_RIGHT_TURN -> listOf("적색 우회전", "40 m", "전방 적색", "멈췄다 가세요")
        GuideKind.DEFOG -> listOf("김서림 주의", "", "내기 순환 중", "성에 제거 켜세요")
    }
    val right = kind == GuideKind.LANE_CHANGE || kind == GuideKind.ROUNDABOUT
    return copy(
        guideKind = kind,
        right = right,
        tone = Tone.WARN,
        turnLabel = title,
        distanceLabel = distance,
        signalLabel = status,
        badge = headline,
        message = previewHowTo(kind, right),
    )
}

/** 미리보기 한 줄 문구이자 음성. 레버를 쓰는 안내는 레버 동작, 아니면 해야 할 일. */
fun previewHowTo(kind: GuideKind, right: Boolean): String =
    leverMotion(kind, right)?.howTo ?: when (kind) {
        GuideKind.DEFOG -> "앞유리 성에 제거 버튼을 누르고, 공조를 외기 순환으로 바꾸세요"
        GuideKind.SPEED -> "가속 페달에서 발을 떼고 천천히 속도를 줄이세요"
        GuideKind.BRAKE -> "앞차와 거리를 넉넉히 두고, 브레이크는 미리 천천히 밟으세요"
        else -> "정지선에서 완전히 멈췄다가 우회전하세요"
    }

/** 미리보기 상태를 팝업으로 그대로 옮긴다. */
fun CoachState.previewPopup(): Popup = Popup(PopupTone.WARN, guideKind, right, turnLabel, distanceLabel, badge)
