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
import kotlin.math.abs

// spec 3.1 파라미터
const val APPROACH_START_M = 100
const val GUIDE_LEAD_MS = 4000
const val GUIDE_MIN_M = 50.0
const val GUIDE_MAX_M = 100.0
const val CHECK_AT_M = 30
const val TURN_DONE_DEG = 60.0
const val TURN_TIMEOUT_MS = 30000L
const val TBT_JUMP_M = 20

private const val KPH_PER_METER_PER_SECOND = 3.6
private const val MS_PER_SECOND = 1000.0

/** 두 진행 방향의 차이 (0~180°). 예: 350 → 10 이면 20. */
fun headingDiff(from: Double, to: Double): Double =
    abs((to - from + 540).mod(360.0) - 180)

/** 이 속도에서 안내를 시작할 거리 (m). 판정 지점까지 GUIDE_LEAD_MS가 남는 거리다. */
fun guideDistance(speedKph: Double): Double {
    val metersPerSecond = speedKph / KPH_PER_METER_PER_SECOND
    val leadMeters = metersPerSecond * GUIDE_LEAD_MS / MS_PER_SECOND
    return (CHECK_AT_M + leadMeters).coerceIn(GUIDE_MIN_M, GUIDE_MAX_M)
}

/**
 * R1: 회전·유턴 전 깜빡이 (spec 3장).
 *
 * @param nextEventId 엔진 전체에서 순서대로 이벤트 id를 만들어주는 함수
 */
class R1Rule(private val nextEventId: () -> String) {
    private sealed class State {
        data object Idle : State()

        data class Approaching(
            val targetTurn: TurnType,
            val guided: Boolean,
            /** 직전 프레임의 tbt.distanceM. 거리가 다시 커지면 새 회전으로 본다 (#2a). */
            val lastDistanceM: Int,
        ) : State()

        data class Turning(
            val targetTurn: TurnType,
            val guided: Boolean,
            val signalOk: Boolean,
            val startHeading: Double,
            val movingMs: Long,
            val lastT: Long,
        ) : State()
    }

    private var state: State = State.Idle

    /** R1이 회전에 다가가거나 회전 중이면 true. R2가 같은 회전을 두 번 세지 않게 쓴다 (spec 11.3 #2). */
    val isActive: Boolean get() = state !is State.Idle

    /** 프레임 하나를 처리한다. spec 3.3 전이표를 위에서부터 검사해 처음 맞는 행만 적용한다. */
    fun onFrame(frame: SignalFrame): List<EngineOutput> {
        val current = updateMovingTime(state, frame)
        val tbt = frame.tbt

        when {
            // 전이표 #0: 주차·후진으로 보고 취소
            frame.gear == Gear.P || frame.gear == Gear.R -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #1a: 처음 본 회전이 이미 30m 안. 다가오는 과정 없이 바로 판정한다 (연속 회전)
            current is State.Idle && tbt != null && isTurn(tbt.turn) &&
                tbt.distanceM <= CHECK_AT_M -> {
                state = startTurning(tbt.turn, guided = false, frame)
                return emptyList()
            }

            // 전이표 #1: 다음 회전이 감시 거리 안으로 들어옴
            current is State.Idle && tbt != null && isTurn(tbt.turn) &&
                tbt.distanceM <= APPROACH_START_M -> {
                state = State.Approaching(targetTurn = tbt.turn, guided = false, lastDistanceM = tbt.distanceM)
                return emptyList()
            }

            // 전이표 #2: 경로 재탐색으로 보고 취소
            current is State.Approaching && (tbt == null || tbt.turn != current.targetTurn) -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #2a: 같은 방향인데 거리가 다시 커짐. 앞 회전을 놓치고 다음 회전이 온 것으로 보고 새로 감시한다
            current is State.Approaching && tbt != null &&
                tbt.distanceM > current.lastDistanceM + TBT_JUMP_M -> {
                state = State.Approaching(targetTurn = tbt.turn, guided = false, lastDistanceM = tbt.distanceM)
                return emptyList()
            }

            // 전이표 #3: 30m 지점에서 깜빡이 판정. 기록은 회전이 끝난 뒤에 한다
            current is State.Approaching && tbt != null && tbt.distanceM <= CHECK_AT_M -> {
                state = startTurning(current.targetTurn, current.guided, frame)
                return emptyList()
            }

            // 전이표 #4: 안내 거리 안인데 깜빡이가 안 켜져 있으면 한 번만 안내
            current is State.Approaching && tbt != null &&
                tbt.distanceM > CHECK_AT_M &&
                tbt.distanceM <= guideDistance(frame.speedKph) &&
                !isSameDirection(frame.turnSignal, current.targetTurn) &&
                !current.guided -> {
                state = current.copy(guided = true, lastDistanceM = tbt.distanceM)
                return listOf(Guide(frame.t, RuleId.R1, guideText(current.targetTurn), guideAction(current.targetTurn)))
            }

            // 전이표 #5: 진행 방향이 충분히 바뀌어 회전이 끝남. 이때 기록한다
            current is State.Turning &&
                headingDiff(current.startHeading, frame.headingDeg) >= TURN_DONE_DEG -> {
                state = State.Idle
                return listOf(
                    Event(
                        id = nextEventId(),
                        t = frame.t,
                        rule = RuleId.R1,
                        result = if (current.signalOk) Result.KEPT else Result.MISSED,
                        guided = current.guided,
                        turn = current.targetTurn,
                        lat = frame.lat,
                        lon = frame.lon,
                    ),
                )
            }

            // 전이표 #6: 주행했는데도 회전이 안 끝남. 회전하지 않은 것으로 보고 취소
            current is State.Turning && current.movingMs > TURN_TIMEOUT_MS -> {
                state = State.Idle
                return emptyList()
            }

            // 맞는 행이 없으면 상태를 유지한다. APPROACHING이면 이번 거리를 기억한다 (#2a용)
            else -> {
                state = if (current is State.Approaching && tbt != null) {
                    current.copy(lastDistanceM = tbt.distanceM)
                } else {
                    current
                }
                return emptyList()
            }
        }
    }

    /** #1a·#3: 30m 지점의 깜빡이와 진행 방향을 기억하고 회전이 끝나길 기다린다. */
    private fun startTurning(targetTurn: TurnType, guided: Boolean, frame: SignalFrame): State =
        State.Turning(
            targetTurn = targetTurn,
            guided = guided,
            signalOk = isSameDirection(frame.turnSignal, targetTurn),
            startHeading = frame.headingDeg,
            movingMs = 0,
            lastT = frame.t,
        )

    /** spec 3.2: TURNING이면 전이표를 보기 전에 주행 시간을 먼저 갱신한다. */
    private fun updateMovingTime(state: State, frame: SignalFrame): State {
        if (state !is State.Turning) {
            return state
        }

        val elapsedMs = if (frame.speedKph > 0) frame.t - state.lastT else 0
        return state.copy(movingMs = state.movingMs + elapsedMs, lastT = frame.t)
    }

    /** R1이 감시하는 안내: 좌회전, 우회전, 유턴 (spec 3장). */
    private fun isTurn(turn: TurnType): Boolean =
        turn == TurnType.LEFT || turn == TurnType.RIGHT || turn == TurnType.UTURN

    /** 유턴은 왼쪽 깜빡이가 맞다 (시행령 별표 2 1호). HAZARD(비상등)는 같은 방향으로 치지 않는다. */
    private fun isSameDirection(signal: TurnSignal, turn: TurnType): Boolean = when (turn) {
        TurnType.LEFT, TurnType.UTURN -> signal == TurnSignal.LEFT
        TurnType.RIGHT -> signal == TurnSignal.RIGHT
        else -> false
    }

    private fun guideAction(turn: TurnType): GuideAction =
        if (turn == TurnType.RIGHT) GuideAction.SIGNAL_RIGHT else GuideAction.SIGNAL_LEFT

    private fun guideText(turn: TurnType): String = when (turn) {
        TurnType.UTURN -> "유턴 곧이에요, 왼쪽 깜빡이 켜세요"
        TurnType.RIGHT -> "우회전 곧이에요, 깜빡이 켜세요"
        else -> "좌회전 곧이에요, 깜빡이 켜세요"
    }
}
