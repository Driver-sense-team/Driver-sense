package kkam.engine.rules

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType

// spec 7.1 파라미터 (GUIDE_LEAD_MS는 R1과 같은 값을 쓴다)
const val LANE_START_M = 300
const val LANE_CHECK_M = 100
const val LANE_GUIDE_MAX_M = 300.0

private const val KPH_PER_METER_PER_SECOND = 3.6
private const val MS_PER_SECOND = 1000.0

/** 이 속도에서 나들목 안내를 시작할 거리 (m). 판정 지점까지 GUIDE_LEAD_MS가 남는 거리다. */
fun laneGuideDistance(speedKph: Double): Double {
    val metersPerSecond = speedKph / KPH_PER_METER_PER_SECOND
    val leadMeters = metersPerSecond * GUIDE_LEAD_MS / MS_PER_SECOND
    return (LANE_CHECK_M + leadMeters).coerceAtMost(LANE_GUIDE_MAX_M)
}

/**
 * R4: 나들목 진출입 전 깜빡이 (spec 7장).
 *
 * 놓침(MISSED)은 내지 않는다. 차로 번호를 몰라 차로를 바꿀 필요가 있었는지 알 수 없기 때문이다.
 *
 * @param nextEventId 엔진 전체에서 순서대로 이벤트 id를 만들어주는 함수
 */
class R4Rule(private val nextEventId: () -> String) {
    private sealed class State {
        data object Idle : State()

        data class Approaching(
            /** RAMP_LEFT 또는 RAMP_RIGHT */
            val targetTurn: TurnType,
            val guided: Boolean,
        ) : State()
    }

    private var state: State = State.Idle

    /** 프레임 하나를 처리한다. spec 7.3 전이표를 위에서부터 검사해 처음 맞는 행만 적용한다. */
    fun onFrame(frame: SignalFrame): List<EngineOutput> {
        val current = state
        val tbt = frame.tbt

        when {
            // 전이표 #0: 주차·후진으로 보고 취소
            frame.gear == Gear.P || frame.gear == Gear.R -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #1: 나들목이 감시 거리 안으로 들어옴
            current is State.Idle && tbt != null && isRamp(tbt.turn) &&
                tbt.distanceM <= LANE_START_M -> {
                state = State.Approaching(targetTurn = tbt.turn, guided = false)
                return emptyList()
            }

            // 전이표 #2: 경로 재탐색으로 보고 취소
            current is State.Approaching && (tbt == null || tbt.turn != current.targetTurn) -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #3: 100m 지점에서 같은 방향 깜빡이가 켜져 있으면 지킴
            current is State.Approaching && tbt != null && tbt.distanceM <= LANE_CHECK_M &&
                isSameDirection(frame.turnSignal, current.targetTurn) -> {
                state = State.Idle
                return listOf(
                    Event(
                        id = nextEventId(),
                        t = frame.t,
                        rule = RuleId.R4,
                        result = Result.KEPT,
                        guided = current.guided,
                        turn = direction(current.targetTurn),
                        lat = frame.lat,
                        lon = frame.lon,
                    ),
                )
            }

            // 전이표 #4: 100m 지점에서 깜빡이가 꺼져 있음. 기록 없이 끝낸다 (MVP 단순화)
            current is State.Approaching && tbt != null && tbt.distanceM <= LANE_CHECK_M -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #5: 안내 거리 안인데 깜빡이가 안 켜져 있으면 한 번만 안내
            current is State.Approaching && tbt != null &&
                tbt.distanceM > LANE_CHECK_M &&
                tbt.distanceM <= laneGuideDistance(frame.speedKph) &&
                !isSameDirection(frame.turnSignal, current.targetTurn) &&
                !current.guided -> {
                state = current.copy(guided = true)
                return listOf(
                    Guide(frame.t, RuleId.R4, guideText(current.targetTurn), guideAction(current.targetTurn)),
                )
            }

            // 맞는 행이 없으면 상태를 유지한다
            else -> return emptyList()
        }
    }

    private fun isRamp(turn: TurnType): Boolean =
        turn == TurnType.RAMP_LEFT || turn == TurnType.RAMP_RIGHT

    /** HAZARD(비상등)는 같은 방향으로 치지 않는다. */
    private fun isSameDirection(signal: TurnSignal, turn: TurnType): Boolean = when (turn) {
        TurnType.RAMP_LEFT -> signal == TurnSignal.LEFT
        TurnType.RAMP_RIGHT -> signal == TurnSignal.RIGHT
        else -> false
    }

    /** EVENT의 turn에는 LEFT, RIGHT만 넣는다 (spec 2.3). */
    private fun direction(turn: TurnType): TurnType =
        if (turn == TurnType.RAMP_LEFT) TurnType.LEFT else TurnType.RIGHT

    private fun guideAction(turn: TurnType): GuideAction =
        if (turn == TurnType.RAMP_LEFT) GuideAction.SIGNAL_LEFT else GuideAction.SIGNAL_RIGHT

    private fun guideText(turn: TurnType): String =
        if (turn == TurnType.RAMP_LEFT) {
            "왼쪽 나들목이 곧이에요, 깜빡이 켜세요"
        } else {
            "오른쪽 나들목이 곧이에요, 깜빡이 켜세요"
        }
}
