package kkam.app

import kkam.engine.model.Gear
import kkam.engine.model.HeadlightState
import kkam.engine.model.LightPhase
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TrafficLight
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kkam.engine.model.WiperMode
import kkam.engine.rules.CHECK_AT_M
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * PT용 "목적지 경로 코치" 데모. 실제 도로 경로(assets/route_demo_sejong_pangyo.json, 세종대학교 → 포티투닷 판교 약 25.6 km)를
 * 따라 차가 달리는 프레임을 만든다.
 *
 * 에뮬레이터 내비는 지도도 안 나오고 위치도 움직이지 않는다. 그래서 앱 안 네이버 지도를 내비로 보고,
 * 위치, 진행 방향, 다음 안내(TBT)와 남은 거리, 전방 신호, 전조등, 비·와이퍼·공조를 이 파일이 경로 좌표로 계산한다.
 * 같은 경로를 넣으면 항상 같은 프레임이 나온다 (현재 시각, 랜덤을 쓰지 않는다). Android에 의존하지 않아서 JVM 테스트로 확인한다.
 *
 * 시간 압축: 25 km를 3분 남짓에 보여준다. 코치 지점([Cue]) 앞뒤([window])만 실제 속도로 1초씩 가고,
 * 그 밖은 빨리 감는다 (1초 프레임에 최대 [FAST_STEP_M]씩). 빨리 감는 구간은 속도를 30 km/h보다 높게 둬서
 * R2(깜빡이 없이 회전)가 굽은 길을 회전으로 세지 않는다. 엔진이 시각과 거리로 판정하는 구간은 모두 실제 속도 구간 안이다.
 *
 * 흐름: 출발 전 기어 P(경로 미리보기, 앱은 "출발"을 누를 때까지 첫 프레임에서 기다린다) → 코치 지점마다 안내·판정 → 도착해 기어 P(경로 리포트)
 */
object RouteDemo {
    const val ASSET = "route_demo_sejong_pangyo.json"

    /** 코치 지점에서 운전자가 어떻게 하는지. 시연에서 지킴·놓침 장면이 고루 나오게 데모 파일에 적는다. */
    enum class Driver {
        /** 안내를 듣고 켠다 → 안내 후 지킴 */
        AFTER_GUIDE,

        /** 안내 전에 먼저 켠다 → 안내 없이 지킴 */
        EARLY,

        /** 끝까지 안 켠다 → 안내 후 놓침 */
        NEVER,

        /** 안내를 듣고도 늦게, 판정 지점(30 m)을 지난 [LATE_ON_M]에서야 켠다 → 안내 후 놓침 (깜빡이 타이밍 장면) */
        LATE,
    }

    /**
     * 경로 위 코치 지점 종류. 차선 변경은 엔진 룰이 없어서 앱 팝업만 띄우고 판정·리포트에 넣지 않는다.
     * 비는 내비 안내가 아니라 날씨다. 이 지점에서 비가 오기 시작해 와이퍼(R6)·김서림(R7) 안내가 차례로 나온다 ([rainAt]).
     */
    enum class CueKind(
        /** 내비 안내(TBT)로 엔진에 넣는 값. 차선 변경은 내비 안내가 아니라 null. */
        val turn: TurnType?,
    ) {
        LEFT(TurnType.LEFT),
        RIGHT(TurnType.RIGHT),
        RAMP_RIGHT(TurnType.RAMP_RIGHT),
        TUNNEL(TurnType.TUNNEL),
        LANE_CHANGE(null),
        RAIN(null),
        /** 유턴 (R1, 왼쪽 깜빡이). */
        UTURN(TurnType.UTURN),
        /** 회전교차로 진입·진출 (R8). 진출 지점은 진입 지점 바로 뒤(원을 도는 거리)에 둔다. */
        ROUNDABOUT_ENTER(TurnType.ROUNDABOUT_ENTER),
        ROUNDABOUT_EXIT(TurnType.ROUNDABOUT_EXIT),

        // 아래는 영상용 각본 안내다. 엔진 판정이 없고 앱이 카드와 음성만 낸다 (차선 변경과 같다). 시각표는 [scriptFor].
        /** 급정거: 이 지점에서 앞차 때문에 급하게 멈추고, 멈춘 뒤 "앞차와 거리를 두세요". */
        SUDDEN_STOP(null),
        /** 1차로 정속 주행: "1차로는 추월할 때만, 오른쪽 차로로". */
        LANE_KEEP(null),
        /** 깜빡이 뒤 이동 타이밍: 오른쪽 깜빡이를 켜고 3초 기다렸다 옮긴다. */
        LANE_WAIT(null),
        /** 곡선 구간: 커브 앞에서 [CURVE_KPH]까지 줄인다. */
        CURVE(null),
        /** 고속도로 진출로: 나들목(자동차전용도로 끝)부터 [EXIT_SLOW_M]에 걸쳐 시내 속도까지 천천히 줄인다. 카드는 이 지점에서 띄운다. */
        EXIT_SLOW(null),
        /** 진출 직후 회전: 바로 회전하니 그쪽 끝 차로로 미리 옮긴다. */
        PRE_LANE(null),
        /** 브레이크 타이밍: 회전 앞에서 미리 속도를 줄인다. */
        BRAKE_EARLY(null),
    }

    /** 경로 데모 파일의 코치 지점 하나. [point]는 [DemoRoute.points]의 번호. */
    data class Cue(
        val kind: CueKind,
        val point: Int,
        val road: String,
        val driver: Driver? = null,
        /** 이 우회전 앞 신호가 적색인가 (R3 데모 값). */
        val redLight: Boolean = false,
        /** 차선 변경 방향. 오른쪽 차로로 옮기면 true. 차선 변경 지점에만 쓴다 (데모 파일의 "side": "LEFT" | "RIGHT", 없으면 오른쪽). */
        val toRight: Boolean = true,
    )

    data class DemoRoute(
        val start: String,
        val destination: String,
        val points: List<LatLon>,
        val cues: List<Cue>,
        /** 자동차전용도로 구간 (꼭짓점 번호). 이 구간은 [EXPRESS_KPH]로 달린다. */
        val expressway: IntRange,
    )

    /**
     * 각본 카드 하나 (엔진 판정 없음). [showAt]에 주황 안내, [okAt]에 파랑 "좋아요"(null이면 주황만), [hideAt]에 숨김.
     * [toRight]는 차로 관련 카드의 방향이다. [signalFrom]~[signalUntil] 동안 그쪽 깜빡이를 켠다 (없으면 null).
     * 문구와 음성은 앱이 [kind]로 고른다 (PopupPolicy.scriptPopup).
     */
    data class ScriptCue(
        val kind: CueKind,
        val showAt: Long,
        val okAt: Long?,
        val hideAt: Long,
        val toRight: Boolean = true,
        val signalFrom: Long? = null,
        val signalUntil: Long? = null,
    )

    /** 데모 한 번: 경로 정보, 재생할 프레임, 도착 시각, 빨리 감는 프레임 시각, 각본 카드 시각. */
    data class Run(
        val plan: RoutePlan,
        val frames: List<SignalFrame>,
        val arriveAt: Long,
        val fastForward: Set<Long>,
        val scriptCues: List<ScriptCue>,
    )

    /** 출발 전 미리보기 프레임의 길이. 앱은 첫 프레임만 띄우고 "출발"을 누르면 이 시각의 프레임부터 재생한다. */
    const val PREVIEW_MS = 8000L
    const val FRAME_MS = 1000L
    /** 시내 순항 속도. */
    const val CITY_KPH = 40.0
    /** 자동차전용도로 순항 속도. */
    const val EXPRESS_KPH = 80.0
    /** 예상 소요 시간([expectedMinutes])에 쓰는 평균 속도. 시내는 신호 대기를 넣어 순항보다 느리다. */
    const val CITY_AVG_KPH = 25.0
    const val EXPRESS_AVG_KPH = 60.0
    const val TURN_KPH = 15.0
    /**
     * 회전교차로 안을 도는 속도. 진입~진출이 30 m 안팎이라 진출 안내 → 깜빡이 → 판정이 차례로 보이게 회전보다 느리게 돈다.
     * 회전교차로 안(다음 지점이 진출)에서는 이 속도를 그대로 지킨다.
     */
    const val ROUNDABOUT_KPH = 10.0
    /**
     * [Driver.AFTER_GUIDE]가 회전교차로 진출 오른쪽 깜빡이를 켜는 거리 (진출 안내 뒤, 판정 10 m 바로 앞. R8 `RB_EXIT_CHECK_M` 이상).
     * 진입 깜빡이는 진입 지점을 [SIGNAL_OFF_PAST_M] 지나야 끄므로, 실제로는 그때 오른쪽으로 바뀐다.
     */
    const val ROUNDABOUT_EXIT_ON_M = 18
    /**
     * 회전 지점 이 거리 앞부터 [TURN_KPH]까지 줄인다. 일찍 줄여서 회전 앞을 천천히 지나므로
     * 주황 "지금 켜세요" 카드가 영상에서 읽을 수 있을 만큼(약 4초) 보인다.
     */
    const val SLOW_FROM_M = 100.0
    /** 1초에 올릴 수 있는 속도. */
    const val ACCEL_KPH_PER_FRAME = 8.0
    /**
     * 도착 지점 이 거리 앞부터 일정하게 브레이크를 밟아 멈춘다 (속도 ∝ √남은 거리, [targetKph]).
     * 남은 거리에 비례해 줄이면 마지막 몇 m를 5 km/h로 오래 기어가서, 도착에서 리포트까지 15초 넘게 걸렸다.
     */
    const val STOP_FROM_M = 30.0
    const val MIN_KPH = 5.0
    /** [Driver.EARLY]가 회전 깜빡이를 켜는 거리 (안내 거리 50~100m보다 앞). */
    const val EARLY_ON_M = 95
    /**
     * [Driver.AFTER_GUIDE]가 회전 깜빡이를 켜는 거리 (안내 뒤, 판정 30m 바로 앞).
     * 30 이상이어야 한다: 판정 지점(30m 안)에 처음 닿는 프레임에서 깜빡이가 늘 켜져 있게.
     */
    const val AFTER_GUIDE_ON_M = 32
    /** [Driver.AFTER_GUIDE]가 나들목 깜빡이를 켜는 거리 (80 km/h 안내 약 189m 뒤, 판정 100m 바로 앞. [RAMP_CHECK_M] 이상). */
    const val RAMP_ON_M = 110
    /** R4가 나들목 깜빡이를 판정하는 거리 (spec 7.1 LANE_CHECK_M). 이 지점을 지나면 나들목 깜빡이를 끈다. */
    const val RAMP_CHECK_M = 100
    /** 회전 지점을 이만큼 지나면 깜빡이를 끈다. */
    const val SIGNAL_OFF_PAST_M = 15.0
    /** 터널 입구 이 거리 안에서 전조등을 켠다 (R5 안내 200m에서 약 4초 뒤). 켠 뒤로는 계속 켜 둔다. */
    const val HEADLIGHT_ON_M = 100
    /**
     * 적색 우회전 장면 각본. 깜빡이 안내와 정지 안내가 한 프레임에 겹치면 정지 카드만 보이고 음성도 끊긴다.
     * 그래서 신호가 늦게 적색으로 바뀌게 해서 두 안내를 차례로 보여준다.
     * R3는 판정 지점([kkam.engine.rules.CHECK_AT_M] 30m) 앞에서만 안내하므로 50~30m 사이를 [RED_CREEP_KPH]로 천천히 지난다.
     *
     * | 회전까지 | 차 | 엔진·카드 |
     * | --- | --- | --- |
     * | 약 50m | 깜빡이 꺼짐 | R1 안내 "우회전 곧이에요, 깜빡이 켜세요" → 주황 카드 |
     * | [RED_SIGNAL_ON_M] | 오른쪽 깜빡이 켬 | 파랑 "좋아요" |
     * | [RED_FROM_M] | 신호가 적색으로 바뀜 | R3 감시 시작, 다음 프레임에 안내 "멈췄다 가세요" → 주황 카드 |
     * | 정지선 ([STOP_LINE_M]) | 멈춤 | 파랑 "좋아요" |
     */
    const val RED_SIGNAL_ON_M = 41
    /** 적색이 되는 거리. 회전을 지나 [SIGNAL_OFF_PAST_M]까지 적색이다. */
    const val RED_FROM_M = 38
    /** 적색 우회전 앞 [RED_CREEP_FROM_M] ~ 30m를 지나는 속도. 1초에 약 2m씩 가서 두 안내 사이에 몇 초씩 둔다. */
    const val RED_CREEP_KPH = 8.0
    const val RED_CREEP_FROM_M = 50.0
    /** 적색 우회전에서 멈추는 정지선 (회전 지점 앞, R3 판정 30m 안). */
    const val STOP_LINE_M = 8.0
    /** 정지선에서 서 있는 프레임 수. */
    const val RED_STOP_FRAMES = 3
    /** 차선 변경: 팝업 뒤 깜빡이를 켜기까지(주황 4초), 켠 채로 옮기는 시간, 파랑으로 둔 뒤 숨기기까지. */
    const val LANE_SIGNAL_AFTER_MS = 4000L
    const val LANE_SIGNAL_MS = 3000L
    const val LANE_OK_MS = 3000L
    /** [Driver.LATE]가 회전 깜빡이를 켜는 거리. 판정 지점(30 m)을 지난 뒤라 놓침이다. */
    const val LATE_ON_M = 20
    /** 급정거 각본: 앞차 때문에 이 속도들로 1초씩 급하게 줄여 멈춘다 (40 → 18 → 4 km/h, 약 3 m/s²). */
    val SUDDEN_STOP_KPH = listOf(18.0, 4.0)
    /** 커브 각본: 커브 앞 지점부터 [CURVE_SLOW_M]에 걸쳐 [CURVE_KPH]까지 줄이고, 지점부터 [CURVE_HOLD_M]까지 그 속도를 지킨다. */
    const val CURVE_KPH = 60.0
    const val CURVE_SLOW_M = 80.0
    const val CURVE_HOLD_M = 400.0
    /** 진출로 각본: 나들목을 지난 뒤 이 거리에 걸쳐 자동차전용도로 속도에서 시내 속도로 천천히 줄인다 (원래는 바로 40이 됐다). */
    const val EXIT_SLOW_M = 150.0
    /** 각본 카드 지점 뒤로 실제 속도로 가는 거리. 80 km/h에서 카드가 다 보이는 7초가 약 160 m다. */
    const val SCRIPT_AFTER_M = 170.0

    /**
     * 각본 카드 시각표. [t]는 카드를 띄우는 프레임 시각이다 (급정거는 멈춘 프레임, 그 밖은 지점을 지나는 프레임).
     *
     * | 종류 | 주황 | 파랑 "좋아요" | 숨김 | 깜빡이 |
     * | --- | --- | --- | --- | --- |
     * | 차선 변경 | 0초 | 4초 | 10초 | 4~7초 |
     * | 급정거 | 0초 | 없음 | 5초 | 없음 |
     * | 1차로 정속 주행 | 0초 | 없음 | 5초 | 없음 |
     * | 깜빡이 뒤 3초 | 0초 | 3.5초 (3초 기다렸다 옮김) | 7초 | 0~5초 |
     * | 커브 | 0초 | 4초 (60까지 줄임) | 7초 | 없음 |
     * | 진출로 | 0초 | 3.5초 | 5.5초 | 없음 |
     * | 진출 직후 회전 차로 | 0초 | 3초 | 6초 | 2~4.5초 |
     * | 브레이크 타이밍 | 0초 | 3초 | 5.5초 (R1 안내보다 먼저 숨긴다) | 없음 |
     */
    fun scriptFor(kind: CueKind, t: Long, toRight: Boolean): ScriptCue? = when (kind) {
        CueKind.LANE_CHANGE -> ScriptCue(
            kind, t, t + LANE_SIGNAL_AFTER_MS, t + LANE_SIGNAL_AFTER_MS + LANE_SIGNAL_MS + LANE_OK_MS, toRight,
            t + LANE_SIGNAL_AFTER_MS, t + LANE_SIGNAL_AFTER_MS + LANE_SIGNAL_MS,
        )
        CueKind.SUDDEN_STOP -> ScriptCue(kind, t, null, t + 5000)
        CueKind.LANE_KEEP -> ScriptCue(kind, t, null, t + 5000, toRight)
        CueKind.LANE_WAIT -> ScriptCue(kind, t, t + 3500, t + 7000, toRight, t, t + 5000)
        CueKind.CURVE -> ScriptCue(kind, t, t + 4000, t + 7000)
        CueKind.EXIT_SLOW -> ScriptCue(kind, t, t + 3500, t + 5500)
        CueKind.PRE_LANE -> ScriptCue(kind, t, t + 3000, t + 6000, toRight, t + 2000, t + 4500)
        CueKind.BRAKE_EARLY -> ScriptCue(kind, t, t + 3000, t + 5500)
        else -> null
    }
    /**
     * 비 구간 각본. 비가 오기 시작한 프레임부터 잰 시각이다. 와이퍼·내기 순환·성에 제거는 비 오기 전부터 꺼짐·켬·꺼짐이다.
     *
     * | 시각 | 차 | 엔진 (spec 10장 R6, 13장 R7) |
     * | --- | --- | --- |
     * | 0초 | 비 시작, 와이퍼 꺼짐 | R6 시간 재기 |
     * | 5초 | | R6 안내 "와이퍼 켜세요" (RAIN_HOLD_MS) |
     * | [WIPER_ON_AFTER_MS] | 와이퍼 켬 | R6 지킴. R7 시간 재기 (비 + 내기 순환 + 성에 제거 꺼짐) |
     * | 와이퍼 + 10초 | | R7 안내 "성에 제거를 켜고 외기로" (FOG_HOLD_MS) |
     * | [DEFROST_ON_AFTER_MS] | 성에 제거 켬 | R7 지킴 |
     * | [RAIN_END_AFTER_MS] | 비 그침, 와이퍼·성에 제거 끔 | 대기 |
     */
    const val WIPER_ON_AFTER_MS = 9000L
    const val DEFROST_ON_AFTER_MS = 23000L
    const val RAIN_END_AFTER_MS = 27000L
    /** 비 구간을 실제 속도로 가는 거리: 비가 그칠 때까지 자동차전용도로 순항 속도로 달리는 거리 + 2초. 비 지점은 자동차전용도로 위에 둔다. */
    const val RAIN_WINDOW_M = (RAIN_END_AFTER_MS + 2 * FRAME_MS) / 1000.0 * EXPRESS_KPH / 3.6
    /** 빨리 감는 구간에서 한 프레임에 가는 최대 거리. */
    const val FAST_STEP_M = 5000.0
    /** 실제 속도 구간이 이보다 가까우면 이어서 실제 속도로 간다 (짧은 빨리 감기로 화면이 튀지 않게). */
    const val MERGE_GAP_M = 100.0
    /** 출발 직후 이 거리까지는 실제 속도로 출발하는 모습을 보여준다. */
    const val DEPART_M = 60.0
    /** 도착 전 이 거리부터 실제 속도. */
    const val ARRIVE_M = 40.0
    /** 도착 후 기어 P로 서 있는 프레임 수. */
    const val PARKED_FRAMES = 3

    /**
     * 코치 지점마다 실제 속도로 가는 구간: 지점 앞 [first]m ~ 지점 뒤 [second]m.
     * 앞 거리는 엔진이 지점을 보기 시작하는 거리보다 길다 (R1·R3 100m, R5 안내 200m, R4 300m).
     */
    fun window(kind: CueKind): Pair<Double, Double> = when (kind) {
        // 회전 뒤는 판정(회전을 마친 직후)이 나올 만큼만. 길면 마지막 회전에서 도착까지가 늘어진다.
        CueKind.LEFT, CueKind.RIGHT, CueKind.UTURN -> 120.0 to 25.0
        CueKind.ROUNDABOUT_ENTER -> 120.0 to 10.0
        CueKind.ROUNDABOUT_EXIT -> 0.0 to 25.0
        CueKind.LANE_CHANGE -> 20.0 to 130.0
        CueKind.SUDDEN_STOP -> 60.0 to 60.0
        CueKind.LANE_KEEP, CueKind.LANE_WAIT, CueKind.CURVE, CueKind.EXIT_SLOW, CueKind.PRE_LANE -> 20.0 to SCRIPT_AFTER_M
        CueKind.BRAKE_EARLY -> 20.0 to 20.0
        CueKind.TUNNEL -> 260.0 to 30.0
        CueKind.RAMP_RIGHT -> 320.0 to 20.0
        CueKind.RAIN -> 0.0 to RAIN_WINDOW_M
    }

    /**
     * assets의 경로 데모 JSON을 읽는다. 모양:
     * {"start", "destination", "points": [[lat, lon], ...], "cues": [{"kind", "point", "road", "driver"?, "redLight"?, "side"?}], "expressway": {"from", "to"}}
     */
    fun parse(text: String): DemoRoute {
        val root = Json.parseToJsonElement(text).jsonObject
        val express = root.getValue("expressway").jsonObject
        return DemoRoute(
            start = root.getValue("start").jsonPrimitive.content,
            destination = root.getValue("destination").jsonPrimitive.content,
            points = root.getValue("points").jsonArray.map {
                val (lat, lon) = it.jsonArray.map { v -> v.jsonPrimitive.double }
                LatLon(lat, lon)
            },
            cues = root.getValue("cues").jsonArray.map {
                val c = it.jsonObject
                Cue(
                    kind = CueKind.valueOf(c.getValue("kind").jsonPrimitive.content),
                    point = c.getValue("point").jsonPrimitive.int,
                    road = c.getValue("road").jsonPrimitive.content,
                    driver = c["driver"]?.jsonPrimitive?.content?.let(Driver::valueOf),
                    redLight = c["redLight"]?.jsonPrimitive?.boolean ?: false,
                    toRight = c["side"]?.jsonPrimitive?.content != "LEFT",
                )
            },
            expressway = express.getValue("from").jsonPrimitive.int..express.getValue("to").jsonPrimitive.int,
        )
    }

    fun build(route: DemoRoute): Run {
        val path = Path(route.points)
        val cues = route.cues.map { Placed(it, path.distanceAt(it.point)) }
        val navi = cues.filter { it.cue.kind.turn != null }
        val express = path.distanceAt(route.expressway.first)..path.distanceAt(route.expressway.last)
        val windows = realTimeWindows(cues, path.length)
        val frames = mutableListOf<SignalFrame>()
        val fast = mutableSetOf<Long>()
        val scriptCues = mutableListOf<ScriptCue>()
        var rainStartAt: Long? = null
        var t = 0L
        val exitSlow = cues.firstOrNull { it.cue.kind == CueKind.EXIT_SLOW }

        /**
         * 순항 속도. 자동차전용도로 80, 시내 40.
         * 진출로 각본이 있으면 자동차전용도로가 끝난 곳(나들목)부터 [EXIT_SLOW_M]에 걸쳐 80에서 40으로 줄인다 (원래는 바로 40이었다).
         */
        fun cruise(s: Double): Double {
            val exitFrom = express.endInclusive
            return when {
                s in express -> EXPRESS_KPH
                exitSlow != null && s >= exitFrom && s < exitFrom + EXIT_SLOW_M ->
                    EXPRESS_KPH - (EXPRESS_KPH - CITY_KPH) * (s - exitFrom) / EXIT_SLOW_M
                else -> CITY_KPH
            }
        }
        fun emit(s: Double, kph: Double, gear: Gear, brake: Boolean, tbt: Tbt?) {
            frames += SignalFrame(
                t = t,
                speedKph = kph,
                gear = gear,
                steeringDeg = 0.0,
                turnSignal = if (gear == Gear.D) signalAt(cues, s) else TurnSignal.OFF,
                brake = brake,
                lat = path.pointAt(s).lat,
                lon = path.pointAt(s).lon,
                headingDeg = path.headingAt(s),
                tbt = tbt,
                light = lightAt(cues, s),
                headlight = headlightAt(cues, s),
            )
        }

        // 출발 전: 기어 P로 서 있다. 내비는 첫 안내를 이미 알고 있다.
        while (t < PREVIEW_MS) {
            emit(0.0, 0.0, Gear.P, brake = true, tbt = tbtAt(navi, path, 0.0))
            t += FRAME_MS
        }

        // 주행: 실제 속도 구간은 1초 동안 그 속도로, 빨리 감는 구간은 다음 실제 속도 구간 앞까지 크게 나아간다.
        var s = 0.0
        var kph = 0.0
        val stopped = mutableSetOf<Placed>()
        val scriptShown = mutableSetOf<Placed>()
        while (s < path.length) {
            val realTime = windows.any { s >= it.start && s < it.endInclusive }
            if (!realTime) {
                val next = windows.firstOrNull { it.start > s }?.start ?: path.length
                kph = cruise(s)
                emit(s, kph, Gear.D, brake = false, tbt = tbtAt(navi, path, s))
                fast += t
                s = min(next, s + FAST_STEP_M)
                t += FRAME_MS
                continue
            }

            // 적색 우회전: 정지선에 닿으면 정지선에 맞춰 서서 [RED_STOP_FRAMES]만큼 기다린다
            val red = cues.firstOrNull { it.cue.redLight && it !in stopped && it.atM - STOP_LINE_M - s in 0.0..stepM(kph) }
            if (red != null) {
                stopped += red
                s = red.atM - STOP_LINE_M
                repeat(RED_STOP_FRAMES) {
                    emit(s, 0.0, Gear.D, brake = true, tbt = tbtAt(navi, path, s))
                    t += FRAME_MS
                }
                kph = 0.0
                continue
            }

            // 급정거: 지점에 닿으면 앞차 때문에 [SUDDEN_STOP_KPH]로 1초씩 급하게 줄여 멈추고, 멈춘 프레임에 카드를 띄운다
            val suddenStop = cues.firstOrNull { it.cue.kind == CueKind.SUDDEN_STOP && it !in scriptShown && s >= it.atM }
            if (suddenStop != null) {
                scriptShown += suddenStop
                SUDDEN_STOP_KPH.forEach { v ->
                    emit(s, v, Gear.D, brake = true, tbt = tbtAt(navi, path, s))
                    s += stepM(v)
                    if (v == SUDDEN_STOP_KPH.last()) scriptFor(CueKind.SUDDEN_STOP, t, true)?.let { scriptCues += it }
                    t += FRAME_MS
                }
                kph = SUDDEN_STOP_KPH.last()
                continue
            }

            // 그 밖의 각본 카드: 지점을 지나는 프레임에 띄운다 (시각표는 [scriptFor])
            cues.firstOrNull { it.cue.kind != CueKind.SUDDEN_STOP && it !in scriptShown && s >= it.atM && scriptFor(it.cue.kind, 0, true) != null }?.let {
                scriptShown += it
                scriptFor(it.cue.kind, t, it.cue.toRight)?.let { cue -> scriptCues += cue }
            }
            if (rainStartAt == null && cues.any { it.cue.kind == CueKind.RAIN && s >= it.atM }) rainStartAt = t

            val target = targetKph(cues, stopped, path.length, s, cruise(s))
            val next = min(target, kph + ACCEL_KPH_PER_FRAME)
            emit(s, next, Gear.D, brake = next < kph, tbt = tbtAt(navi, path, s))
            kph = next
            s += stepM(kph)
            t += FRAME_MS
        }

        // 도착: 멈추고 기어 P. 내비 안내도 끝난다.
        val end = path.length
        val arriveAt = t
        emit(end, 0.0, Gear.D, brake = true, tbt = null)
        repeat(PARKED_FRAMES) {
            t += FRAME_MS
            emit(end, 0.0, Gear.P, brake = true, tbt = null)
        }

        // 각본 카드 깜빡이 (차선 변경, 깜빡이 뒤 3초, 진출 직후 회전 차로): [ScriptCue.signalFrom]~[ScriptCue.signalUntil] 동안 그쪽 깜빡이를 켠다.
        // 엔진에는 차선 변경 룰이 없고, 이 동안 속도가 30 km/h보다 높아 R2도 회전으로 세지 않는다.
        // 나들목 깜빡이: 차로를 옮긴 뒤(R4 판정 100m 지점을 지난 뒤) 끈다.
        // 켜 둔 채로 나들목까지 가면 R4가 같은 나들목을 다시 지킴으로 센다 (판정 뒤 대기로 돌아가 300m 안이면 다시 감시한다).
        val rampChecked = frames.firstOrNull { it.tbt?.turn == TurnType.RAMP_RIGHT && (it.tbt?.distanceM ?: Int.MAX_VALUE) <= RAMP_CHECK_M }?.t
        val withLaneSignal = frames.map { f ->
            val lane = scriptCues.firstOrNull { cue ->
                val from = cue.signalFrom ?: return@firstOrNull false
                f.t >= from && f.t < (cue.signalUntil ?: from)
            }
            val rampDone = rampChecked != null && f.t > rampChecked && f.tbt?.turn == TurnType.RAMP_RIGHT
            val signal = when {
                lane != null -> if (lane.toRight) TurnSignal.RIGHT else TurnSignal.LEFT
                rampDone -> TurnSignal.OFF
                else -> f.turnSignal
            }
            rainAt(rainStartAt, f.t).applyTo(f.copy(turnSignal = signal))
        }

        val plan = RoutePlan(
            destination = route.destination,
            distanceM = path.length.roundToInt(),
            turns = navi.map { it.cue.kind.turn!! },
            path = route.points,
            durationMin = expectedMinutes(path.length, express.endInclusive - express.start),
        )
        return Run(plan, withLaneSignal, arriveAt, fast, scriptCues)
    }

    /**
     * 미리보기에 보여줄 예상 소요 시간 (분, 올림). 데모 재생 시간이 아니라 실제로 달리면 걸릴 시간이다.
     * 자동차전용도로는 [EXPRESS_AVG_KPH], 나머지 시내는 신호 대기를 포함한 [CITY_AVG_KPH]로 어림한다.
     */
    fun expectedMinutes(totalM: Double, expressM: Double): Int {
        val hours = expressM / 1000.0 / EXPRESS_AVG_KPH + (totalM - expressM) / 1000.0 / CITY_AVG_KPH
        return ceil(hours * 60).toInt()
    }

    /**
     * 회전 앞 몇 m부터 줄일지. 보통 [SLOW_FROM_M]이고, 회전 앞에 브레이크 타이밍 각본([CueKind.BRAKE_EARLY])이 있으면
     * 그 카드가 뜨는 지점부터 줄인다 ("미리 속도를 줄이세요" → 실제로 줄어든다).
     */
    private fun slowFrom(cues: List<Placed>, turn: Placed): Double {
        val brake = cues.lastOrNull { it.cue.kind == CueKind.BRAKE_EARLY && it.atM < turn.atM && turn.atM - it.atM < 300.0 }
        return brake?.let { turn.atM - it.atM } ?: SLOW_FROM_M
    }

    /** 앞에서 속도를 줄이는 코치 지점: 회전, 유턴, 회전교차로. */
    private val SLOW_TURNS = setOf(CueKind.LEFT, CueKind.RIGHT, CueKind.UTURN, CueKind.ROUNDABOUT_ENTER, CueKind.ROUNDABOUT_EXIT)
    private val ROUNDABOUT = setOf(CueKind.ROUNDABOUT_ENTER, CueKind.ROUNDABOUT_EXIT)

    /** 데모 파일의 코치 지점과 경로 위 거리. */
    private data class Placed(val cue: Cue, val atM: Double)

    /** 한 시각의 비와 운전자 조작. */
    data class Weather(val rain: Boolean, val wiper: WiperMode, val recirc: Boolean, val defroster: Boolean) {
        fun applyTo(frame: SignalFrame) = frame.copy(rain = rain, wiper = wiper, recirc = recirc, defroster = defroster)
    }

    /**
     * [rainStartAt](비가 오기 시작한 시각, 비 지점이 없으면 null) 기준으로 [t]의 비와 조작. 표는 [WIPER_ON_AFTER_MS].
     * 내기 순환은 처음부터 켜 둔다 (흔한 설정이고, R7이 김서림 상황으로 보는 조건이다).
     */
    fun rainAt(rainStartAt: Long?, t: Long): Weather {
        val since = rainStartAt?.let { t - it }
        val raining = since != null && since >= 0 && since < RAIN_END_AFTER_MS
        return Weather(
            rain = raining,
            wiper = if (raining && since!! >= WIPER_ON_AFTER_MS) WiperMode.ON else WiperMode.OFF,
            recirc = true,
            defroster = raining && since!! >= DEFROST_ON_AFTER_MS,
        )
    }

    private fun stepM(kph: Double) = kph / KPH_PER_MPS * FRAME_MS / MS_PER_SECOND

    /** 실제 속도로 가는 구간들. 출발 직후, 코치 지점 앞뒤, 도착 전. 가까운 구간은 하나로 잇는다. */
    private fun realTimeWindows(cues: List<Placed>, length: Double): List<ClosedFloatingPointRange<Double>> {
        val raw = listOf(0.0..DEPART_M) +
            cues.map { val (before, after) = window(it.cue.kind); (it.atM - before)..(it.atM + after) } +
            listOf((length - ARRIVE_M)..length)
        val merged = mutableListOf<ClosedFloatingPointRange<Double>>()
        raw.sortedBy { it.start }.forEach { w ->
            val last = merged.lastOrNull()
            if (last != null && w.start - last.endInclusive < MERGE_GAP_M) {
                merged[merged.lastIndex] = last.start..max(last.endInclusive, w.endInclusive)
            } else {
                merged += w
            }
        }
        return merged
    }

    /**
     * 다음 회전 속도로 줄이고, 적색 우회전은 정지선 앞에서, 도착 앞에서는 멈출 만큼 줄인다.
     * 적색 우회전은 두 안내를 차례로 보여주려고 [RED_CREEP_FROM_M]~30m를 [RED_CREEP_KPH]로 지난다 ([RED_SIGNAL_ON_M] 표).
     */
    private fun targetKph(cues: List<Placed>, stopped: Set<Placed>, length: Double, s: Double, cruise: Double): Double {
        val turn = cues.firstOrNull { it.atM > s && it.cue.kind in SLOW_TURNS }
        val forTurn = when {
            turn == null -> cruise
            turn.cue.redLight && turn !in stopped -> {
                val toLine = turn.atM - STOP_LINE_M - s
                val slowing = if (toLine < SLOW_FROM_M) cruise * toLine / SLOW_FROM_M else cruise
                val creeping = turn.atM - s in CHECK_AT_M.toDouble()..RED_CREEP_FROM_M
                if (creeping) min(slowing, RED_CREEP_KPH) else slowing
            }
            turn.cue.kind == CueKind.ROUNDABOUT_EXIT -> ROUNDABOUT_KPH
            turn.atM - s < slowFrom(cues, turn) -> {
                val slowest = if (turn.cue.kind in ROUNDABOUT) ROUNDABOUT_KPH else TURN_KPH
                slowest + (cruise - slowest) * (turn.atM - s) / slowFrom(cues, turn)
            }
            else -> cruise
        }
        val toEnd = length - s
        val forStop = if (toEnd < STOP_FROM_M) cruise * sqrt(toEnd / STOP_FROM_M) else cruise
        // 커브 각본: 지점부터 [CURVE_SLOW_M]에 걸쳐 [CURVE_KPH]까지 줄이고 [CURVE_HOLD_M]까지 지킨다
        val curve = cues.firstOrNull { it.cue.kind == CueKind.CURVE && s >= it.atM && s < it.atM + CURVE_HOLD_M }
        val forCurve = when {
            curve == null || cruise <= CURVE_KPH -> cruise
            s < curve.atM + CURVE_SLOW_M -> cruise - (cruise - CURVE_KPH) * (s - curve.atM) / CURVE_SLOW_M
            else -> CURVE_KPH
        }
        return maxOf(MIN_KPH, minOf(forTurn, forStop, forCurve))
    }

    /** 다음 내비 안내와 남은 거리. 마지막 안내 뒤에는 목적지까지 직진. */
    private fun tbtAt(navi: List<Placed>, path: Path, s: Double): Tbt {
        val next = navi.firstOrNull { it.atM > s }
        return if (next != null) {
            Tbt(next.cue.kind.turn!!, (next.atM - s).roundToInt())
        } else {
            Tbt(TurnType.STRAIGHT, (path.length - s).roundToInt())
        }
    }

    /** 운전자 각본대로 깜빡이. 지점 앞에서 켜고, 지나서 [SIGNAL_OFF_PAST_M] 뒤에 끈다. */
    private fun signalAt(cues: List<Placed>, s: Double): TurnSignal {
        for (placed in cues) {
            val ahead = placed.atM - s
            if (ahead < -SIGNAL_OFF_PAST_M) continue
            val signal = when (placed.cue.kind) {
                CueKind.LEFT -> TurnSignal.LEFT
                CueKind.RIGHT, CueKind.RAMP_RIGHT, CueKind.ROUNDABOUT_EXIT -> TurnSignal.RIGHT
                // 유턴과 회전교차로 진입은 왼쪽 (시행령 별표 2 1호·7호)
                CueKind.UTURN, CueKind.ROUNDABOUT_ENTER -> TurnSignal.LEFT
                CueKind.TUNNEL, CueKind.LANE_CHANGE, CueKind.RAIN, CueKind.SUDDEN_STOP, CueKind.LANE_KEEP, CueKind.LANE_WAIT,
                CueKind.CURVE, CueKind.EXIT_SLOW, CueKind.PRE_LANE, CueKind.BRAKE_EARLY -> continue
            }
            val onAt = when (placed.cue.driver) {
                Driver.EARLY -> EARLY_ON_M
                Driver.AFTER_GUIDE -> when {
                    placed.cue.kind == CueKind.RAMP_RIGHT -> RAMP_ON_M
                    placed.cue.redLight -> RED_SIGNAL_ON_M
                    placed.cue.kind == CueKind.ROUNDABOUT_EXIT -> ROUNDABOUT_EXIT_ON_M
                    else -> AFTER_GUIDE_ON_M
                }
                Driver.LATE -> LATE_ON_M
                Driver.NEVER, null -> return TurnSignal.OFF
            }
            return if (ahead <= onAt) signal else TurnSignal.OFF
        }
        return TurnSignal.OFF
    }

    /** 적색 우회전 앞 [RED_FROM_M]부터 회전을 지날 때까지 적색. 그 밖은 신호 정보 없음. */
    private fun lightAt(cues: List<Placed>, s: Double): TrafficLight? {
        val red = cues.any { it.cue.redLight && it.atM - s <= RED_FROM_M && s - it.atM <= SIGNAL_OFF_PAST_M }
        return if (red) TrafficLight(LightPhase.RED, null) else null
    }

    /** 터널 입구 [HEADLIGHT_ON_M] 앞부터 전조등을 켜고 계속 켜 둔다. 그 전에는 꺼져 있다. */
    private fun headlightAt(cues: List<Placed>, s: Double): HeadlightState {
        val tunnel = cues.firstOrNull { it.cue.kind == CueKind.TUNNEL } ?: return HeadlightState.OFF
        return if (s >= tunnel.atM - HEADLIGHT_ON_M) HeadlightState.ON else HeadlightState.OFF
    }

    /** 경로 좌표를 따라 거리(m)로 위치와 진행 방향을 찾는다. */
    private class Path(private val points: List<LatLon>) {
        /** 출발점에서 각 꼭짓점까지의 거리 (m). */
        private val cumulative: List<Double> =
            points.zipWithNext { a, b -> meters(a, b) }.runningFold(0.0) { total, step -> total + step }
        val length: Double get() = cumulative.last()

        fun distanceAt(point: Int): Double = cumulative[point]

        /** [s]가 들어 있는 구간 번호. 꼭짓점에 딱 걸리면 다음 구간 (회전 뒤 방향). */
        private fun segmentAt(s: Double): Int =
            (1 until points.size).firstOrNull { cumulative[it] > s }?.minus(1) ?: (points.size - 2)

        fun pointAt(s: Double): LatLon {
            val i = segmentAt(s)
            val span = cumulative[i + 1] - cumulative[i]
            val f = if (span == 0.0) 0.0 else ((s - cumulative[i]) / span).coerceIn(0.0, 1.0)
            val a = points[i]
            val b = points[i + 1]
            return LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
        }

        fun headingAt(s: Double): Double {
            val i = segmentAt(s)
            return bearing(points[i], points[i + 1])
        }
    }

    private const val KPH_PER_MPS = 3.6
    private const val MS_PER_SECOND = 1000.0
}

/** 두 좌표 사이 거리 (m). 짧은 거리라 평면 근사로 충분하다. */
fun meters(a: LatLon, b: LatLon): Double {
    val dLat = Math.toRadians(b.lat - a.lat) * EARTH_RADIUS_M
    val dLon = Math.toRadians(b.lon - a.lon) * EARTH_RADIUS_M * cos(Math.toRadians((a.lat + b.lat) / 2))
    return sqrt(dLat * dLat + dLon * dLon)
}

/** a에서 b로 가는 방향. 북쪽 0°, 시계 방향 (SignalFrame.headingDeg와 같다). */
fun bearing(a: LatLon, b: LatLon): Double {
    val dLat = b.lat - a.lat
    val dLon = (b.lon - a.lon) * cos(Math.toRadians((a.lat + b.lat) / 2))
    return (Math.toDegrees(atan2(dLon, dLat)) + FULL_TURN_DEG) % FULL_TURN_DEG
}

private const val EARTH_RADIUS_M = 6_371_000.0
private const val FULL_TURN_DEG = 360.0
