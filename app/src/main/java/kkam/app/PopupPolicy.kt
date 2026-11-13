package kkam.app

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.Result
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnType

/** 내비 위 팝업 카드 색. 주황 = 켜세요, 파랑 = 좋아요, 빨강 = 판정 지점을 꺼진 채 지남 ("다음엔 미리 켜요"). */
enum class PopupTone { WARN, OK, MISS }

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
 * | 와이퍼·김서림 카드가 파랑 | 그대로 둔다. 판정 뒤 카드는 내비 안내로 돌아가 대기가 되지만, "좋아요"는 몇 초 보여준다 |
 * | 내비 안내가 띄울 때와 다름 | 숨김 (재탐색이나 지나감) |
 * | 카드가 대기 상태 | 숨김 |
 * | 그 밖 | 띄움. 안내를 따랐으면 파랑, 판정 지점을 꺼진 채 지났으면 빨강 "다음엔 미리 켜요", 아니면 주황 |
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
        current.tone == PopupTone.OK && current.kind.notTiedToNavi() -> current
        current.forTurn != null && current.forTurn != turn -> null
        state.tone == Tone.IDLE -> null
        else -> state.toPopup(current.forTurn)
    }
}

private fun CoachState.toPopup(turn: TurnType?) = Popup(
    tone = when {
        tone == Tone.OK -> PopupTone.OK
        // 판정 지점을 주황인 채로 지나 색이 고정됐으면 놓침. 주행 화면 카드의 MISS와 같은 빨강 (CoachLook)
        checkedTone == Tone.WARN -> PopupTone.MISS
        else -> PopupTone.WARN
    },
    kind = guideKind,
    right = right,
    turnLabel = turnLabel,
    distanceLabel = distanceLabel,
    headline = badge,
    forTurn = turn,
)

/** 내비 안내와 상관없이 뜨는 안내 종류인가. */
private fun GuideKind.notTiedToNavi(): Boolean = this == GuideKind.WIPER || this == GuideKind.DEFOG

/**
 * 차선 변경 팝업 제목과 문구. 엔진 룰이 없어서 경로 데모 각본([RouteDemo.ScriptCue])으로만 띄운다.
 * 제목에 옮겨 갈 쪽을 적는다 ("오른쪽 차로로"). 안내 종류 미리보기와 같은 모양이다.
 */
fun laneChangeTitle(toRight: Boolean): String = if (toRight) "오른쪽 차로로" else "왼쪽 차로로"
const val LANE_CHANGE_HEADLINE = "깜빡이 켜고 옮기세요"

/** 차선 변경 팝업을 띄울 때 읽는 문장. 어느 쪽으로 옮기는지, 어느 깜빡이를 켜는지 말한다. */
fun laneChangeSpeech(toRight: Boolean): String =
    if (toRight) "오른쪽 차로로 옮겨요, 오른쪽 깜빡이 켜세요" else "왼쪽 차로로 옮겨요, 왼쪽 깜빡이 켜세요"

/** 각본 카드 한 종류의 모습: 그림 종류, 제목, 주황 알약 문구, 음성. 파랑일 때 알약은 "좋아요". */
data class ScriptLook(val kind: GuideKind, val title: String, val headline: String, val speech: String)

/**
 * 경로 데모 각본 카드의 문구 (영상용, 엔진 판정 없음). 시각표는 [RouteDemo.scriptFor].
 *
 * | 종류 | 제목 · 주황 알약 | 음성 |
 * | --- | --- | --- |
 * | 차선 변경 | 오른쪽 차로로 · 깜빡이 켜고 옮기세요 | 오른쪽 차로로 옮겨요, 오른쪽 깜빡이 켜세요 |
 * | 급정거 | 급정거 · 앞차와 거리를 두세요 | 방금 급하게 멈췄어요, 앞차와 거리를 넉넉히 두세요 |
 * | 1차로 정속 주행 | 1차로 정속 주행 · 오른쪽 차로로 옮기세요 | 1차로는 추월할 때만 써요, 오른쪽 차로로 옮기세요 |
 * | 깜빡이 뒤 3초 | 오른쪽 차로로 · 3초 뒤에 옮기세요 | 깜빡이를 켜고 3초쯤 기다렸다 옮기세요 |
 * | 커브 | 커브길 · 60까지 줄이세요 | 커브길이에요, 속도를 60까지 줄이세요 |
 * | 진출로 | 진출로 · 40까지 줄이세요 | 진출로예요, 속도를 천천히 40까지 줄이세요 |
 * | 진출 직후 회전 차로 | 곧 우회전 · 오른쪽 끝 차로로 미리 | 나가자마자 우회전이에요, 오른쪽 끝 차로로 미리 옮기세요 |
 * | 브레이크 타이밍 | 회전 앞 · 미리 속도를 줄이세요 | 회전이 가까워요, 지금부터 천천히 속도를 줄이세요 |
 */
fun scriptLook(kind: RouteDemo.CueKind, toRight: Boolean): ScriptLook? {
    val side = if (toRight) "오른쪽" else "왼쪽"
    return when (kind) {
        RouteDemo.CueKind.LANE_CHANGE ->
            ScriptLook(GuideKind.LANE_CHANGE, laneChangeTitle(toRight), LANE_CHANGE_HEADLINE, laneChangeSpeech(toRight))
        RouteDemo.CueKind.SUDDEN_STOP ->
            ScriptLook(GuideKind.BRAKE, "급정거", "앞차와 거리를 두세요", "방금 급하게 멈췄어요, 앞차와 거리를 넉넉히 두세요")
        RouteDemo.CueKind.LANE_KEEP ->
            ScriptLook(GuideKind.LANE_CHANGE, "1차로 정속 주행", "${side} 차로로 옮기세요", "1차로는 추월할 때만 써요, ${side} 차로로 옮기세요")
        RouteDemo.CueKind.LANE_WAIT ->
            ScriptLook(GuideKind.LANE_CHANGE, laneChangeTitle(toRight), "3초 뒤에 옮기세요", "깜빡이를 켜고 3초쯤 기다렸다 옮기세요")
        RouteDemo.CueKind.CURVE ->
            ScriptLook(GuideKind.SPEED, "커브길", "${RouteDemo.CURVE_KPH.toInt()}까지 줄이세요", "커브길이에요, 속도를 ${RouteDemo.CURVE_KPH.toInt()}까지 줄이세요")
        RouteDemo.CueKind.EXIT_SLOW ->
            ScriptLook(GuideKind.SPEED, "진출로", "${RouteDemo.CITY_KPH.toInt()}까지 줄이세요", "진출로예요, 속도를 천천히 ${RouteDemo.CITY_KPH.toInt()}까지 줄이세요")
        RouteDemo.CueKind.PRE_LANE ->
            ScriptLook(
                GuideKind.LANE_CHANGE, if (toRight) "곧 우회전" else "곧 좌회전", "${side} 끝 차로로 미리",
                "나가자마자 ${if (toRight) "우회전" else "좌회전"}이에요, ${side} 끝 차로로 미리 옮기세요",
            )
        RouteDemo.CueKind.BRAKE_EARLY ->
            ScriptLook(GuideKind.BRAKE, "회전 앞", "미리 속도를 줄이세요", "회전이 가까워요, 지금부터 천천히 속도를 줄이세요")
        else -> null
    }
}

/**
 * 경로 데모의 각본 카드 팝업. 이 시각에 띄울 팝업, 각본 밖이면 null.
 * 판정·리포트에는 넣지 않는다 (엔진 룰이 없다).
 *
 * | 시각 | 팝업 |
 * | --- | --- |
 * | showAt ~ okAt 전 (okAt이 없으면 hideAt 전) | 주황 안내 |
 * | okAt ~ hideAt 전 | 파랑 "좋아요" |
 * | 그 밖 | 없음 |
 */
fun scriptPopup(cues: List<RouteDemo.ScriptCue>, t: Long): Popup? {
    val cue = cues.firstOrNull { t >= it.showAt && t < it.hideAt } ?: return null
    val look = scriptLook(cue.kind, cue.toRight) ?: return null
    val ok = cue.okAt != null && t >= cue.okAt
    return Popup(
        tone = if (ok) PopupTone.OK else PopupTone.WARN,
        kind = look.kind,
        right = cue.toRight,
        turnLabel = look.title,
        distanceLabel = "",
        headline = if (ok) "좋아요" else look.headline,
    )
}

/** 각본 카드를 띄우는 프레임에 읽을 문장. 그 프레임이 아니면 null. */
fun scriptSpeech(cues: List<RouteDemo.ScriptCue>, t: Long): String? =
    cues.firstOrNull { it.showAt == t }?.let { scriptLook(it.kind, it.toRight)?.speech }

/**
 * 기어 P에서 출발해 처음 D가 됐을 때 차량 내비를 앞으로 띄울지 ([CoachService]).
 * 실제 주행과 시나리오 재생에서는 띄운다 (주행 중 평소 화면은 내비, 기획서 4.2).
 * 경로 데모가 돌고 있으면 띄우지 않는다. 앱 지도가 내비 역할이라 내비가 앞에 오면 지도를 가린다.
 */
fun opensNaviOnDeparture(running: String?): Boolean = running != CoachSession.ROUTE_DEMO
