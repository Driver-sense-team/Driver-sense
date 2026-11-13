package kkam.app

import kkam.engine.model.Event
import kkam.engine.model.LawEntry
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.TurnType
import kotlin.math.roundToInt

// 경로 코치: 내비에서 목적지를 찍으면 그 경로를 따라 코치한다.
// 이 파일은 Android에 의존하지 않아서 JVM 테스트로 확인한다 (RouteCoachTest).
// 내비 SDK 이벤트를 받는 쪽은 RouteWatcher, 세션에 붙이는 쪽은 CoachSession이다.

/** 위도·경도 한 점. */
data class LatLon(val lat: Double, val lon: Double)

/** 내비 경로 하나. 경로 안내가 시작될 때 만든다. */
data class RoutePlan(
    /** 목적지 이름. 내비가 이름을 안 주면 "목적지". */
    val destination: String,
    /** 경로 전체 거리 (m). 모르면 0. */
    val distanceM: Int,
    /** 경로 위 회전 안내 순서. 내비 TBT 목록을 엔진 값으로 바꾼 것. */
    val turns: List<TurnType>,
    /** 경로 좌표. 앱 지도에 경로선을 그린다. 내비 SDK는 좌표를 주지 않아서 경로 데모에만 있다. */
    val path: List<LatLon> = emptyList(),
)

/** 경로 주행 상태. */
enum class RouteStatus {
    /** 안내 중 */
    DRIVING,

    /** 목적지 도착 */
    ARRIVED,

    /** 도착 전에 안내 종료·취소 */
    ENDED,
}

/** 지금(또는 마지막) 경로 주행. [firstEventIndex]부터의 오늘 판정이 이 경로의 판정이다. */
data class RouteTrip(
    val plan: RoutePlan,
    val firstEventIndex: Int,
    val status: RouteStatus = RouteStatus.DRIVING,
)

/** 이 경로에서 나온 판정. */
fun RouteTrip.events(todayEvents: List<Event>): List<Event> = todayEvents.drop(firstEventIndex)

/** 코치가 안내하는 회전 종류 (엔진 룰 R1·R4·R5가 보는 내비 안내). 직진·유턴 등은 세지 않는다. */
val COACHED_TURNS = listOf(TurnType.LEFT, TurnType.RIGHT, TurnType.RAMP_LEFT, TurnType.RAMP_RIGHT, TurnType.TUNNEL)

/** 미리보기 칩에 쓰는 이름. 나들목은 방향을 합쳐서 센다. */
private fun TurnType.groupLabel(): String = when (this) {
    TurnType.RAMP_LEFT, TurnType.RAMP_RIGHT -> "나들목"
    else -> label()
}

/** 약한 곳: 자주 놓친 판정 종류와, 그것이 경로 위에서 나오는 회전 종류. */
data class WeakSpot(
    /** 예: "좌회전 깜빡이" */
    val label: String,
    /** 이 약한 곳이 경로 위에서 나오는 회전 종류. 예: 좌회전 깜빡이 → LEFT */
    val turns: Set<TurnType>,
    /** 오늘 놓친 기록에서 왔으면 true, 진단 값에서 왔으면 false. */
    val fromToday: Boolean,
)

/**
 * 자주 놓친 유형을 고른다.
 * 1) 오늘 놓친 판정이 있으면 그중 가장 많이 놓친 종류 (같으면 최근 것).
 * 2) 없으면 진단 값에서 준수율이 가장 낮은 습관.
 * 3) 둘 다 없으면 null.
 */
fun weakSpotOf(todayEvents: List<Event>, diagnosis: Diagnosis?): WeakSpot? {
    val missed = todayEvents.filter { it.result == Result.MISSED }
    if (missed.isNotEmpty()) {
        // maxBy는 같은 개수면 앞의 것을 고르므로, 최근 것이 앞에 오게 뒤집는다.
        val worst = missed.reversed().groupBy { it.title() }.maxBy { it.value.size }.value.first()
        return WeakSpot(worst.title(), worst.routeTurns(), fromToday = true)
    }
    val lowest = diagnosis?.rates?.minByOrNull { it.percent } ?: return null
    return WeakSpot(lowest.habit.label, lowest.habit.routeTurns(), fromToday = false)
}

/** 판정이 경로 위에서 어떤 회전에서 나오는지. R3(적색 우회전)는 우회전에서 나온다. */
private fun Event.routeTurns(): Set<TurnType> = when (rule) {
    RuleId.R1, RuleId.R2 -> setOfNotNull(turn)
    RuleId.R3 -> setOf(TurnType.RIGHT)
    RuleId.R4 -> setOf(TurnType.RAMP_LEFT, TurnType.RAMP_RIGHT)
    RuleId.R5 -> setOf(TurnType.TUNNEL)
    RuleId.R6, RuleId.R7 -> emptySet() // 와이퍼(비)·김서림은 날씨에 달려 있어 경로 위 지점이 없다
}

private fun Habit.routeTurns(): Set<TurnType> = when (this) {
    Habit.LEFT_SIGNAL -> setOf(TurnType.LEFT)
    Habit.RIGHT_SIGNAL, Habit.RED_RIGHT_STOP -> setOf(TurnType.RIGHT)
}

/** 출발 전(기어 P) 경로 미리보기 카드에 그릴 값. */
data class RoutePreview(
    val destination: String,
    /** 예: "4.2 km". 거리를 모르면 빈 문자열. */
    val distanceLabel: String,
    /** 코치할 지점 수 (좌·우회전, 나들목, 터널). */
    val coachPoints: Int,
    /** 종류별 개수. 예: ["좌회전 2", "우회전 1"]. 없는 종류는 뺀다. */
    val counts: List<String>,
    /** 경로 순서. 예: ["좌회전", "우회전", "좌회전"]. 코치할 지점만. */
    val sequence: List<String>,
    val weakSpot: WeakSpot?,
    /** 이번 경로에서 약한 곳이 나오는 횟수. */
    val weakOnRoute: Int,
)

fun buildRoutePreview(plan: RoutePlan, todayEvents: List<Event>, diagnosis: Diagnosis?): RoutePreview {
    val coached = plan.turns.filter { it in COACHED_TURNS }
    val weak = weakSpotOf(todayEvents, diagnosis)
    return RoutePreview(
        destination = plan.destination,
        distanceLabel = distanceLabel(plan.distanceM),
        coachPoints = coached.size,
        counts = coached.groupingBy { it.groupLabel() }.eachCount().map { (label, count) -> "$label $count" },
        sequence = coached.map { it.groupLabel() },
        weakSpot = weak,
        weakOnRoute = weak?.let { spot -> coached.count { it in spot.turns } } ?: 0,
    )
}

/** 1234 → "1.2 km", 800 → "800 m", 0 → "" */
fun distanceLabel(meters: Int): String = when {
    meters <= 0 -> ""
    meters < METERS_PER_KM -> "$meters m"
    else -> "${(meters / 100.0).roundToInt() / 10.0} km"
}

/**
 * 경로 안내가 시작될 때 읽는 한 문장.
 * 예: "판교역까지 안내를 시작해요. 좌회전 2곳, 우회전 1곳에서 챙길게요."
 */
fun routeStartMessage(plan: RoutePlan): String {
    val counts = plan.turns.filter { it in COACHED_TURNS }
        .groupingBy { it.groupLabel() }.eachCount()
        .map { (label, count) -> "$label ${count}곳" }
    val head = "${plan.destination}까지 안내를 시작해요."
    return if (counts.isEmpty()) head else "$head ${counts.joinToString(", ")}에서 챙길게요."
}

/** 경로 리포트 머리 한 줄. 예: "도착했어요 · 3번 중 2번 지켰어요" */
fun routeSummary(trip: RouteTrip, todayEvents: List<Event>): String {
    val events = trip.events(todayEvents)
    val head = when (trip.status) {
        RouteStatus.DRIVING -> "경로 주행 중"
        RouteStatus.ARRIVED -> "도착했어요"
        RouteStatus.ENDED -> "안내가 끝났어요"
    }
    if (events.isEmpty()) return "$head · 아직 판정이 없어요"
    return "$head · ${events.size}번 중 ${events.count { it.result == Result.KEPT }}번 지켰어요"
}

/** 경로 안내가 시작됐을 때 세션이 할 일. */
enum class RouteStartAction {
    /** 멈춰 있었다: 실제 차량 신호 코치를 켜고 경로를 붙인다. */
    START_LIVE,

    /** 이미 실제 차량 신호 코치 중: 경로만 붙인다. */
    ATTACH,

    /** 코치가 꺼져 있거나, 시나리오·경로 데모 재생 중: 건드리지 않는다 (시연을 끊지 않는다). */
    IGNORE,
}

/**
 * 전이표 (코치 켬/끔과 세션이 지금 도는 것 → 경로 시작 이벤트에서 할 일)
 * | 코치 | 지금                 | 할 일      |
 * | 끔   | (무엇이든)            | IGNORE     |
 * | 켬   | 멈춤 (null)          | START_LIVE |
 * | 켬   | 실제 차량 신호 (LIVE)  | ATTACH     |
 * | 켬   | 시나리오·경로 데모     | IGNORE     |
 */
fun routeStartAction(running: String?, coachOn: Boolean): RouteStartAction = when {
    !coachOn -> RouteStartAction.IGNORE
    running == null -> RouteStartAction.START_LIVE
    running == CoachSession.LIVE -> RouteStartAction.ATTACH
    else -> RouteStartAction.IGNORE
}

/** 경로를 붙인다. 이 시점까지의 오늘 판정은 이 경로 판정에서 뺀다. */
fun CoachState.startRoute(plan: RoutePlan): CoachState =
    copy(route = RouteTrip(plan, firstEventIndex = todayEvents.size), message = routeStartMessage(plan))

/**
 * 경로 리포트 카드 목록. 이 경로의 판정만, 놓친 곳(잘못한 점)을 먼저, 그다음 지킨 곳. 같은 종류 안에서는 최근 것이 위.
 * 카드 내용(제목, 조항, 처분)은 오늘 리포트와 같은 [buildReport]로 만든다.
 */
fun routeReportItems(trip: RouteTrip, todayEvents: List<Event>, laws: List<LawEntry> = emptyList()): List<ReportItem> =
    buildReport(trip.events(todayEvents), laws).items.sortedBy { it.kept }

/** 경로가 끝났다. 안내 중인 경로만 끝낸다 (끝난 경로에 도착이 또 와도 바꾸지 않는다). */
fun CoachState.endRoute(status: RouteStatus): CoachState {
    val trip = route ?: return this
    if (trip.status != RouteStatus.DRIVING) return this
    return copy(route = trip.copy(status = status), message = routeSummary(trip.copy(status = status), todayEvents))
}

private const val METERS_PER_KM = 1000
