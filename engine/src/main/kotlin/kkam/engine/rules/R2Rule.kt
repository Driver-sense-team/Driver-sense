package kkam.engine.rules

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType

// spec 11.1 파라미터
const val R2_TURN_DEG = 60.0
const val R2_TURN_WINDOW_MS = 10000L
const val R2_TURN_MAX_KPH = 30.0
const val R2_SIGNAL_LOOKBACK_MS = 5000L
const val R2_REVERSE_SKIP_MS = 30000L
const val R2_OTHER_RULE_SKIP_MS = 10000L

/**
 * R2: 깜빡이 없이 회전 (spec 11장).
 *
 * 내비 안내 없이 최근 프레임의 진행 방향 변화로 회전을 찾고, 깜빡이를 켰는지로 KEPT·MISSED를 기록한다.
 * 안내(GUIDE)는 없다. 다른 룰(R1·R4)이 보는 회전은 판정하지 않는다.
 *
 * @param nextEventId 엔진 전체에서 순서대로 이벤트 id를 만들어주는 함수
 */
class R2Rule(private val nextEventId: () -> String) {
    /** history에 남기는 프레임 한 장. 판정에 필요한 값만 둔다. */
    private data class Sample(
        val t: Long,
        val headingDeg: Double,
        val speedKph: Double,
        val turnSignal: TurnSignal,
    )

    /** 다른 룰이 지금 이 회전을 보고 있는지 알려주는 값. 엔진이 채워 넘긴다. */
    data class OtherRules(
        /** R1이 IDLE이 아니면 true (회전에 다가가거나 회전 중) */
        val r1Active: Boolean,
        /** 마지막으로 R1·R4가 EVENT를 낸 프레임의 t. 이 프레임 것도 포함한다. 없으면 null */
        val lastEventT: Long?,
    )

    /** spec 11.2: 최근 15초의 프레임. 오래된 것이 앞에 있다. */
    private val history = ArrayDeque<Sample>()
    private var lastReverseT: Long? = null

    /** 프레임 하나를 처리한다. spec 11.3 판정 순서를 위에서부터 검사해 처음 맞는 행만 적용한다. */
    fun onFrame(frame: SignalFrame, others: OtherRules): List<EngineOutput> {
        val t = frame.t

        when {
            // 판정 순서 #0: 주차·후진. 기록을 비우고 후진 시각을 기억한다
            frame.gear == Gear.P || frame.gear == Gear.R -> {
                if (frame.gear == Gear.R) lastReverseT = t
                history.clear()
                return emptyList()
            }

            // 판정 순서 #1: 후진한 지 30초가 안 됨. 주차장으로 보고 판정하지 않는다
            lastReverseT?.let { t - it < R2_REVERSE_SKIP_MS } == true -> {
                history.clear()
                return emptyList()
            }

            // 판정 순서 #2: 다른 룰이 이 회전을 본다. 같은 회전을 두 번 세지 않는다
            otherRuleWatches(frame, others) -> {
                history.clear()
                return emptyList()
            }
        }

        history.addLast(Sample(t, frame.headingDeg, frame.speedKph, frame.turnSignal))

        // 판정 순서 #3: 10초 안에 60° 이상, 그 구간 내내 30km/h 이하면 회전으로 본다
        val turnStart = findTurnStart(t, frame.headingDeg)
        if (turnStart != null) {
            val direction = turnDirection(turnStart.headingDeg, frame.headingDeg)
            val signalOk = history.any { it.t >= turnStart.t - R2_SIGNAL_LOOKBACK_MS && isSameDirection(it.turnSignal, direction) }
            history.clear()
            return listOf(
                Event(
                    id = nextEventId(),
                    t = t,
                    rule = RuleId.R2,
                    result = if (signalOk) Result.KEPT else Result.MISSED,
                    guided = false,
                    turn = direction,
                    lat = frame.lat,
                    lon = frame.lon,
                ),
            )
        }

        // 판정 순서 #4: 15초보다 오래된 프레임을 지운다
        while (history.first().t < t - R2_TURN_WINDOW_MS - R2_SIGNAL_LOOKBACK_MS) {
            history.removeFirst()
        }
        return emptyList()
    }

    /** spec 11.3 #2의 "다른 룰이 이 회전을 본다". */
    private fun otherRuleWatches(frame: SignalFrame, others: OtherRules): Boolean {
        val tbt = frame.tbt
        val turnNear = tbt != null && (tbt.turn == TurnType.LEFT || tbt.turn == TurnType.RIGHT) &&
            tbt.distanceM <= APPROACH_START_M
        val rampNear = tbt != null && (tbt.turn == TurnType.RAMP_LEFT || tbt.turn == TurnType.RAMP_RIGHT) &&
            tbt.distanceM <= LANE_START_M
        val recentEvent = others.lastEventT?.let { frame.t - it < R2_OTHER_RULE_SKIP_MS } == true
        return others.r1Active || turnNear || rampNear || recentEvent
    }

    /**
     * 최근 10초 안의 프레임 중 지금과 진행 방향이 60° 이상 차이 나고, 그 프레임부터 지금까지 모두 30km/h 이하인
     * 가장 이른 프레임. 없으면 null.
     */
    private fun findTurnStart(now: Long, nowHeading: Double): Sample? {
        val window = history.filter { it.t >= now - R2_TURN_WINDOW_MS }
        // 뒤에서부터 "이 프레임부터 지금까지 가장 빠른 속도"를 미리 구해 둔다
        val maxSpeedFrom = DoubleArray(window.size)
        var max = 0.0
        for (i in window.indices.reversed()) {
            max = maxOf(max, window[i].speedKph)
            maxSpeedFrom[i] = max
        }
        return window.indices.firstOrNull { i ->
            headingDiff(window[i].headingDeg, nowHeading) >= R2_TURN_DEG && maxSpeedFrom[i] <= R2_TURN_MAX_KPH
        }?.let { window[it] }
    }

    /** 부호 있는 변화가 양수면 시계 방향(RIGHT), 음수면 LEFT. */
    private fun turnDirection(from: Double, to: Double): TurnType {
        val signed = (to - from + 540).mod(360.0) - 180
        return if (signed > 0) TurnType.RIGHT else TurnType.LEFT
    }

    /** HAZARD(비상등)는 같은 방향으로 치지 않는다. */
    private fun isSameDirection(signal: TurnSignal, turn: TurnType): Boolean =
        (turn == TurnType.LEFT && signal == TurnSignal.LEFT) || (turn == TurnType.RIGHT && signal == TurnSignal.RIGHT)
}
