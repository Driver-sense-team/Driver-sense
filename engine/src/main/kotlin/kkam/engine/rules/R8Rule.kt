package kkam.engine.rules

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType

// spec 14.1 파라미터. APPROACH_START_M, CHECK_AT_M, guideDistance는 R1과 같은 값을 쓴다.
const val RB_EXIT_CHECK_M = 10
const val RB_PASS_M = 20

/**
 * R8: 회전교차로 진입·진출 깜빡이 (spec 14장).
 *
 * 들어갈 때는 왼쪽, 나갈 때는 오른쪽 깜빡이를 본다. 진입과 진출을 따로 판정해서 회전교차로 하나에 EVENT가 두 개 나온다.
 * 회전교차로 안은 진행 방향이 계속 바뀌어 R1처럼 60°로 끝을 볼 수 없으므로, 내비의 다음 안내가 바뀌면 통과로 본다.
 *
 * @param nextEventId 엔진 전체에서 순서대로 이벤트 id를 만들어주는 함수
 */
class R8Rule(private val nextEventId: () -> String) {
    private sealed class State {
        data object Idle : State()

        data class Approaching(
            /** ROUNDABOUT_ENTER 또는 ROUNDABOUT_EXIT */
            val target: TurnType,
            val guided: Boolean,
            /** 판정 지점에서 맞는 깜빡이였는가. 판정 지점을 보기 전에는 null. */
            val signalOk: Boolean?,
            /** 직전 프레임의 tbt.distanceM. 다음 안내로 바뀔 때 통과인지 재탐색인지 가른다 (#2·#3). */
            val lastDistanceM: Int,
        ) : State()
    }

    private var state: State = State.Idle

    /** R8이 회전교차로를 보고 있으면 true. R2가 같은 회전교차로를 "깜빡이 없이 회전"으로 세지 않게 쓴다 (spec 11.3 #2). */
    val isActive: Boolean get() = state !is State.Idle

    /** 프레임 하나를 처리한다. spec 14.3 전이표를 위에서부터 검사해 처음 맞는 행만 적용한다. */
    fun onFrame(frame: SignalFrame): List<EngineOutput> {
        val current = state
        val tbt = frame.tbt

        when {
            // 전이표 #0: 주차·후진으로 보고 취소
            frame.gear == Gear.P || frame.gear == Gear.R -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #1: 회전교차로 진입·진출 안내가 감시 거리 안으로 들어옴
            current is State.Idle -> {
                state = start(tbt, frame.turnSignal)
                return emptyList()
            }

            // 전이표 #2: 다음 안내로 바뀌었고 바로 앞이었다 → 그 지점을 통과. 판정 지점의 깜빡이로 기록하고, 다음 안내(진출)를 바로 감시한다
            current is State.Approaching && tbt?.turn != current.target &&
                current.lastDistanceM <= RB_PASS_M && current.signalOk != null -> {
                state = start(tbt, frame.turnSignal)
                return listOf(
                    Event(
                        id = nextEventId(),
                        t = frame.t,
                        rule = RuleId.R8,
                        result = if (current.signalOk) Result.KEPT else Result.MISSED,
                        guided = current.guided,
                        turn = current.target,
                        lat = frame.lat,
                        lon = frame.lon,
                    ),
                )
            }

            // 전이표 #3: 다음 안내로 바뀌었는데 #2가 아님 → 재탐색이거나 판정 지점을 못 봄. 기록 없이 이번 안내로 다시 시작
            current is State.Approaching && tbt?.turn != current.target -> {
                state = start(tbt, frame.turnSignal)
                return emptyList()
            }

            // 전이표 #4: 판정 지점에서 깜빡이를 본다. 한 번 정하면 바꾸지 않는다
            current is State.Approaching && tbt != null && current.signalOk == null &&
                tbt.distanceM <= checkM(current.target) -> {
                state = current.copy(signalOk = isRightSignal(frame.turnSignal, current.target), lastDistanceM = tbt.distanceM)
                return emptyList()
            }

            // 전이표 #5: 안내 거리 안인데 맞는 깜빡이가 아니면 한 번만 안내
            current is State.Approaching && tbt != null && current.signalOk == null &&
                tbt.distanceM <= guideDistance(frame.speedKph) &&
                !isRightSignal(frame.turnSignal, current.target) && !current.guided -> {
                state = current.copy(guided = true, lastDistanceM = tbt.distanceM)
                return listOf(Guide(frame.t, RuleId.R8, guideText(current.target), guideAction(current.target)))
            }

            // 전이표 #6: 그 밖. 이번 거리를 기억한다
            else -> {
                state = if (current is State.Approaching && tbt != null) current.copy(lastDistanceM = tbt.distanceM) else current
                return emptyList()
            }
        }
    }

    /** #1: 이번 안내가 회전교차로이고 감시 거리 안이면 감시를 시작한다. 이미 판정 지점 안이면 그 자리에서 깜빡이를 본다. */
    private fun start(tbt: Tbt?, signal: TurnSignal): State {
        if (tbt == null || !isRoundabout(tbt.turn) || tbt.distanceM > APPROACH_START_M) return State.Idle
        val signalOk = if (tbt.distanceM <= checkM(tbt.turn)) isRightSignal(signal, tbt.turn) else null
        return State.Approaching(target = tbt.turn, guided = false, signalOk = signalOk, lastDistanceM = tbt.distanceM)
    }

    private fun isRoundabout(turn: TurnType): Boolean =
        turn == TurnType.ROUNDABOUT_ENTER || turn == TurnType.ROUNDABOUT_EXIT

    /** 판정 지점: 진입은 R1과 같은 30m, 진출은 회전교차로 안이 짧아 10m. */
    private fun checkM(target: TurnType): Int =
        if (target == TurnType.ROUNDABOUT_EXIT) RB_EXIT_CHECK_M else CHECK_AT_M

    /** 들어갈 때는 왼쪽, 나갈 때는 오른쪽. HAZARD(비상등)는 맞는 깜빡이로 치지 않는다. */
    private fun isRightSignal(signal: TurnSignal, target: TurnType): Boolean =
        signal == if (target == TurnType.ROUNDABOUT_EXIT) TurnSignal.RIGHT else TurnSignal.LEFT

    private fun guideAction(target: TurnType): GuideAction =
        if (target == TurnType.ROUNDABOUT_EXIT) GuideAction.SIGNAL_RIGHT else GuideAction.SIGNAL_LEFT

    private fun guideText(target: TurnType): String =
        if (target == TurnType.ROUNDABOUT_EXIT) EXIT_TEXT else ENTER_TEXT

    companion object {
        const val ENTER_TEXT = "회전교차로에 들어가요, 왼쪽 깜빡이 켜세요"
        const val EXIT_TEXT = "회전교차로에서 나가요, 오른쪽 깜빡이 켜세요"
    }
}
