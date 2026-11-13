package kkam.app

import kkam.engine.model.Gear
import kkam.engine.model.HeadlightState
import kkam.engine.model.LightPhase
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TrafficLight
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlin.math.atan2
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
 * 위치, 진행 방향, 다음 안내(TBT)와 남은 거리, 전방 신호, 전조등을 이 파일이 경로 좌표로 계산한다.
 * 같은 경로를 넣으면 항상 같은 프레임이 나온다 (현재 시각, 랜덤을 쓰지 않는다). Android에 의존하지 않아서 JVM 테스트로 확인한다.
 *
 * 시간 압축: 25 km를 3분 안에 보여준다. 코치 지점([Cue]) 앞뒤([window])만 실제 속도로 1초씩 가고,
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
    }

    /** 경로 위 코치 지점 종류. 차선 변경은 엔진 룰이 없어서 앱 팝업만 띄우고 판정·리포트에 넣지 않는다. */
    enum class CueKind(
        /** 내비 안내(TBT)로 엔진에 넣는 값. 차선 변경은 내비 안내가 아니라 null. */
        val turn: TurnType?,
    ) {
        LEFT(TurnType.LEFT),
        RIGHT(TurnType.RIGHT),
        RAMP_RIGHT(TurnType.RAMP_RIGHT),
        TUNNEL(TurnType.TUNNEL),
        LANE_CHANGE(null),
    }

    /** 경로 데모 파일의 코치 지점 하나. [point]는 [DemoRoute.points]의 번호. */
    data class Cue(
        val kind: CueKind,
        val point: Int,
        val road: String,
        val driver: Driver? = null,
        /** 이 우회전 앞 신호가 적색인가 (R3 데모 값). */
        val redLight: Boolean = false,
    )

    data class DemoRoute(
        val start: String,
        val destination: String,
        val points: List<LatLon>,
        val cues: List<Cue>,
        /** 자동차전용도로 구간 (꼭짓점 번호). 이 구간은 [EXPRESS_KPH]로 달린다. */
        val expressway: IntRange,
    )

    /** 차선 변경 팝업 시각. [showAt]에 주황 안내, [okAt]에 깜빡이를 켜 파랑 "좋아요", [hideAt]에 숨김. */
    data class LaneCue(val showAt: Long, val okAt: Long, val hideAt: Long)

    /** 데모 한 번: 경로 정보, 재생할 프레임, 도착 시각, 빨리 감는 프레임 시각, 차선 변경 팝업 시각. */
    data class Run(
        val plan: RoutePlan,
        val frames: List<SignalFrame>,
        val arriveAt: Long,
        val fastForward: Set<Long>,
        val laneCues: List<LaneCue>,
    )

    /** 출발 전 미리보기 프레임의 길이. 앱은 첫 프레임만 띄우고 "출발"을 누르면 이 시각의 프레임부터 재생한다. */
    const val PREVIEW_MS = 8000L
    const val FRAME_MS = 1000L
    /** 시내 순항 속도. */
    const val CITY_KPH = 40.0
    /** 자동차전용도로 순항 속도. */
    const val EXPRESS_KPH = 80.0
    const val TURN_KPH = 15.0
    /** 회전 지점 이 거리 앞부터 [TURN_KPH]까지 줄인다. */
    const val SLOW_FROM_M = 60.0
    /** 1초에 올릴 수 있는 속도. */
    const val ACCEL_KPH_PER_FRAME = 8.0
    /** 도착 지점 이 거리 앞부터 멈출 때까지 줄인다. */
    const val STOP_FROM_M = 50.0
    const val MIN_KPH = 5.0
    /** [Driver.EARLY]가 회전 깜빡이를 켜는 거리 (안내 거리 50~100m보다 앞). */
    const val EARLY_ON_M = 95
    /** [Driver.AFTER_GUIDE]가 회전 깜빡이를 켜는 거리 (안내 뒤, 판정 30m 전). */
    const val AFTER_GUIDE_ON_M = 42
    /** [Driver.AFTER_GUIDE]가 나들목 깜빡이를 켜는 거리 (80 km/h 안내 약 189m 뒤, 판정 100m 전). */
    const val RAMP_ON_M = 150
    /** R4가 나들목 깜빡이를 판정하는 거리 (spec 7.1 LANE_CHECK_M). 이 지점을 지나면 나들목 깜빡이를 끈다. */
    const val RAMP_CHECK_M = 100
    /** 회전 지점을 이만큼 지나면 깜빡이를 끈다. */
    const val SIGNAL_OFF_PAST_M = 15.0
    /** 터널 입구 이 거리 안에서 전조등을 켠다 (R5 안내 200m 뒤). 켠 뒤로는 계속 켜 둔다. */
    const val HEADLIGHT_ON_M = 150
    /** 적색 신호를 보는 거리 (R1·R3 감시 100m보다 앞). 회전을 지나 [SIGNAL_OFF_PAST_M]까지 적색이다. */
    const val RED_FROM_M = 120
    /** 적색 우회전에서 멈추는 정지선 (회전 지점 앞, R3 판정 30m 안). */
    const val STOP_LINE_M = 8.0
    /** 정지선에서 서 있는 프레임 수. */
    const val RED_STOP_FRAMES = 3
    /** 차선 변경: 팝업 뒤 깜빡이를 켜기까지, 켠 채로 옮기는 시간, 파랑으로 둔 뒤 숨기기까지. */
    const val LANE_SIGNAL_AFTER_MS = 2000L
    const val LANE_SIGNAL_MS = 3000L
    const val LANE_OK_MS = 2000L
    /** 빨리 감는 구간에서 한 프레임에 가는 최대 거리. */
    const val FAST_STEP_M = 1500.0
    /** 실제 속도 구간이 이보다 가까우면 이어서 실제 속도로 간다 (짧은 빨리 감기로 화면이 튀지 않게). */
    const val MERGE_GAP_M = 100.0
    /** 출발 직후 이 거리까지는 실제 속도로 출발하는 모습을 보여준다. */
    const val DEPART_M = 60.0
    /** 도착 전 이 거리부터 실제 속도. */
    const val ARRIVE_M = 80.0
    /** 도착 후 기어 P로 서 있는 프레임 수. */
    const val PARKED_FRAMES = 3

    /**
     * 코치 지점마다 실제 속도로 가는 구간: 지점 앞 [first]m ~ 지점 뒤 [second]m.
     * 앞 거리는 엔진이 지점을 보기 시작하는 거리보다 길다 (R1·R3 100m, R5 안내 200m, R4 300m).
     */
    fun window(kind: CueKind): Pair<Double, Double> = when (kind) {
        CueKind.LEFT, CueKind.RIGHT -> 120.0 to 40.0
        CueKind.LANE_CHANGE -> 20.0 to 90.0
        CueKind.TUNNEL -> 260.0 to 30.0
        CueKind.RAMP_RIGHT -> 320.0 to 20.0
    }

    /**
     * assets의 경로 데모 JSON을 읽는다. 모양:
     * {"start", "destination", "points": [[lat, lon], ...], "cues": [{"kind", "point", "road", "driver"?, "redLight"?}], "expressway": {"from", "to"}}
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
        val laneCues = mutableListOf<LaneCue>()
        var t = 0L

        fun cruise(s: Double) = if (s in express) EXPRESS_KPH else CITY_KPH
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
        val laneShown = mutableSetOf<Placed>()
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

            cues.firstOrNull { it.cue.kind == CueKind.LANE_CHANGE && it !in laneShown && s >= it.atM }?.let {
                laneShown += it
                laneCues += LaneCue(t, t + LANE_SIGNAL_AFTER_MS, t + LANE_SIGNAL_AFTER_MS + LANE_SIGNAL_MS + LANE_OK_MS)
            }

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

        // 차선 변경 깜빡이: 팝업 뒤 [LANE_SIGNAL_AFTER_MS]부터 [LANE_SIGNAL_MS] 동안 오른쪽 깜빡이를 켠다.
        // 엔진에는 차선 변경 룰이 없고, 이 동안 속도가 30 km/h보다 높아 R2도 회전으로 세지 않는다.
        // 나들목 깜빡이: 차로를 옮긴 뒤(R4 판정 100m 지점을 지난 뒤) 끈다.
        // 켜 둔 채로 나들목까지 가면 R4가 같은 나들목을 다시 지킴으로 센다 (판정 뒤 대기로 돌아가 300m 안이면 다시 감시한다).
        val rampChecked = frames.firstOrNull { it.tbt?.turn == TurnType.RAMP_RIGHT && (it.tbt?.distanceM ?: Int.MAX_VALUE) <= RAMP_CHECK_M }?.t
        val withLaneSignal = frames.map { f ->
            val laneOn = laneCues.any { f.t >= it.okAt && f.t < it.okAt + LANE_SIGNAL_MS }
            val rampDone = rampChecked != null && f.t > rampChecked && f.tbt?.turn == TurnType.RAMP_RIGHT
            when {
                laneOn -> f.copy(turnSignal = TurnSignal.RIGHT)
                rampDone -> f.copy(turnSignal = TurnSignal.OFF)
                else -> f
            }
        }

        val plan = RoutePlan(
            destination = route.destination,
            distanceM = path.length.roundToInt(),
            turns = navi.map { it.cue.kind.turn!! },
            path = route.points,
        )
        return Run(plan, withLaneSignal, arriveAt, fast, laneCues)
    }

    /** 데모 파일의 코치 지점과 경로 위 거리. */
    private data class Placed(val cue: Cue, val atM: Double)

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

    /** 다음 회전 속도로 줄이고, 적색 우회전은 정지선 앞에서, 도착 앞에서는 멈출 만큼 줄인다. */
    private fun targetKph(cues: List<Placed>, stopped: Set<Placed>, length: Double, s: Double, cruise: Double): Double {
        val turn = cues.firstOrNull { it.atM > s && (it.cue.kind == CueKind.LEFT || it.cue.kind == CueKind.RIGHT) }
        val forTurn = when {
            turn == null -> cruise
            turn.cue.redLight && turn !in stopped -> {
                val toLine = turn.atM - STOP_LINE_M - s
                if (toLine < SLOW_FROM_M) cruise * toLine / SLOW_FROM_M else cruise
            }
            turn.atM - s < SLOW_FROM_M -> TURN_KPH + (cruise - TURN_KPH) * (turn.atM - s) / SLOW_FROM_M
            else -> cruise
        }
        val toEnd = length - s
        val forStop = if (toEnd < STOP_FROM_M) cruise * toEnd / STOP_FROM_M else cruise
        return maxOf(MIN_KPH, minOf(forTurn, forStop))
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
                CueKind.RIGHT, CueKind.RAMP_RIGHT -> TurnSignal.RIGHT
                CueKind.TUNNEL, CueKind.LANE_CHANGE -> continue
            }
            val onAt = when (placed.cue.driver) {
                Driver.EARLY -> EARLY_ON_M
                Driver.AFTER_GUIDE -> if (placed.cue.kind == CueKind.RAMP_RIGHT) RAMP_ON_M else AFTER_GUIDE_ON_M
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
