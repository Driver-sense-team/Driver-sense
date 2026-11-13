package kkam.app

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.Result
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnType

/** 내비 위 팝업 카드 색. 주황 = 켜세요, 파랑 = 좋아요. */
enum class PopupTone { WARN, OK }

/** 내비 위에 띄울 작은 안내 카드. 값은 모두 [CoachState]에서 온다. */
data class Popup(
    val tone: PopupTone,
    /** 그림을 고르는 데 쓴다. */
    val kind: GuideKind,
    /** 오른쪽 방향이면 true. 화살표와 레버 그림 방향. */
    val right: Boolean,
    /** 예: "좌회전" */
    val turnLabel: String,
    /** 예: "60 m" */
    val distanceLabel: String,
    /** 예: "지금 켜세요", "좋아요" */
    val headline: String,
    /** 팝업을 띄운 내비 안내. 내비 안내가 이것과 달라지면(재탐색, 지나감) 숨긴다. 미리보기는 null. */
    val forTurn: TurnType? = null,
)

/**
 * 프레임 하나를 반영한 다음 팝업. null이면 숨긴다. 위에서부터 처음 맞는 줄을 쓴다.
 *
 * | 조건 | 결과 |
 * | --- | --- |
 * | 기어 P | 숨김 (정차 중에는 앱 화면이 리포트를 보여준다) |
 * | 이 프레임에 음성 안내(GUIDE)가 있음 | 띄움. 같은 프레임에 다른 판정이 있어도 새 안내가 먼저다 |
 * | 놓침(MISSED) 판정 | 숨김. 위반 직후 잔소리하지 않는다 (기획서 5.4) |
 * | 지킴(KEPT) 판정이고 떠 있음 | 파랑 "좋아요". 안내 직후 바로 켜도(R5) 파랑이 보이게 한다 |
 * | 숨겨져 있음 | 계속 숨김. 안내 전에는 먼저 띄우지 않는다 |
 * | 내비 안내가 띄울 때와 다름 | 숨김 (재탐색이나 지나감) |
 * | 카드가 대기 상태 | 숨김 |
 * | 그 밖 | 띄움. 안내를 따랐으면 파랑, 아니면 주황 |
 *
 * 파랑으로 바뀐 뒤 몇 초 지나 숨기는 것은 [CoachSession]이 시간을 재서 한다.
 */
fun nextPopup(current: Popup?, state: CoachState, outputs: List<EngineOutput>, frame: SignalFrame): Popup? {
    val turn = frame.tbt?.turn
    return when {
        frame.gear == Gear.P -> null
        // 와이퍼·김서림 안내는 내비 안내와 상관없어서 내비 안내가 바뀌어도 숨기지 않는다
        outputs.any { it is Guide } -> state.toPopup(if (state.guideKind.notTiedToNavi()) null else turn)
        outputs.any { it is Event && it.result == Result.MISSED } -> null
        outputs.any { it is Event } && current != null -> current.copy(
            tone = PopupTone.OK,
            headline = "좋아요",
            distanceLabel = state.distanceLabel.ifEmpty { current.distanceLabel },
        )
        current == null -> null
        current.forTurn != null && current.forTurn != turn -> null
        state.tone == Tone.IDLE -> null
        else -> state.toPopup(current.forTurn)
    }
}

private fun CoachState.toPopup(turn: TurnType?) = Popup(
    tone = if (tone == Tone.OK) PopupTone.OK else PopupTone.WARN,
    kind = guideKind,
    right = right,
    turnLabel = turnLabel,
    distanceLabel = distanceLabel,
    headline = badge,
    forTurn = turn,
)

/** 내비 안내와 상관없이 뜨는 안내 종류인가. */
private fun GuideKind.notTiedToNavi(): Boolean = this == GuideKind.WIPER || this == GuideKind.DEFOG

/** 차선 변경 팝업 제목과 문구. 엔진 룰이 없어서 경로 데모 각본([RouteDemo.LaneCue])으로만 띄운다. */
const val LANE_CHANGE_TITLE = "차선 변경"
const val LANE_CHANGE_HEADLINE = "깜빡이 켜고 옮기세요"
/** 차선 변경 팝업을 띄울 때 읽는 문장. */
const val LANE_CHANGE_SPEECH = "차선 바꿀 때 깜빡이 켜세요"

/**
 * 경로 데모의 차선 변경 팝업. 이 시각에 띄울 팝업, 각본 밖이면 null.
 * 판정·리포트에는 넣지 않는다 (엔진에 차선 변경 룰이 없다). 그림은 미리보기와 같은 차선 변경 레버 그림이다.
 *
 * | 시각 | 팝업 |
 * | --- | --- |
 * | showAt ~ okAt 전 | 주황 "깜빡이 켜고 옮기세요" + 레버 그림 |
 * | okAt ~ hideAt 전 | 파랑 "좋아요" (깜빡이를 켰다) |
 * | 그 밖 | 없음 |
 */
fun laneChangePopup(cues: List<RouteDemo.LaneCue>, t: Long): Popup? {
    val cue = cues.firstOrNull { t >= it.showAt && t < it.hideAt } ?: return null
    val ok = t >= cue.okAt
    return Popup(
        tone = if (ok) PopupTone.OK else PopupTone.WARN,
        kind = GuideKind.LANE_CHANGE,
        right = true,
        turnLabel = LANE_CHANGE_TITLE,
        distanceLabel = "",
        headline = if (ok) "좋아요" else LANE_CHANGE_HEADLINE,
    )
}

/**
 * 기어 P에서 출발해 처음 D가 됐을 때 차량 내비를 앞으로 띄울지 ([CoachService]).
 * 실제 주행과 시나리오 재생에서는 띄운다 (주행 중 평소 화면은 내비, 기획서 4.2).
 * 경로 데모가 돌고 있으면 띄우지 않는다. 앱 지도가 내비 역할이라 내비가 앞에 오면 지도를 가린다.
 */
fun opensNaviOnDeparture(running: String?): Boolean = running != CoachSession.ROUTE_DEMO
