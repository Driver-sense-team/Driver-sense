package kkam.engine.rules

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.LightPhase
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnType

// spec 9.1 파라미터. APPROACH_START_M, CHECK_AT_M, guideDistance, TURN_DONE_DEG, TURN_TIMEOUT_MS는 R1과 같은 값을 쓴다.
const val STOP_KPH = 2.0
const val TURN_START_DEG = 15.0

/**
 * R3: 적색 신호 우회전 일시정지 (spec 9장).
 *
 * 전방 신호가 적색인 우회전에서, 회전을 시작하기 전에 한 번이라도 멈췄는지 본다.
 * 신호(light)가 없으면 아무것도 하지 않는다.
 *
 * @param nextEventId 엔진 전체에서 순서대로 이벤트 id를 만들어주는 함수
 */
class R3Rule(private val nextEventId: () -> String) {
    private sealed class State {
        data object Idle : State()

        data class Approaching(
            val guided: Boolean,
            val stopped: Boolean,
        ) : State()

        data class Turning(
            val guided: Boolean,
            val stopped: Boolean,
            val startHeading: Double,
            val movingMs: Long,
            val lastT: Long,
        ) : State()
    }

    private var state: State = State.Idle

    /** 프레임 하나를 처리한다. spec 9.3 전이표를 위에서부터 검사해 처음 맞는 행만 적용한다. */
    fun onFrame(frame: SignalFrame): List<EngineOutput> {
        val current = updateMovingTime(state, frame)
        val tbt = frame.tbt
        val red = frame.light?.phase == LightPhase.RED
        val stoppedNow = frame.speedKph <= STOP_KPH

        when {
            // 전이표 #0: 주차·후진으로 보고 취소
            frame.gear == Gear.P || frame.gear == Gear.R -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #1: 적색인 우회전이 감시 거리 안으로 들어옴
            current is State.Idle && tbt != null && tbt.turn == TurnType.RIGHT &&
                tbt.distanceM <= APPROACH_START_M && red -> {
                state = State.Approaching(guided = false, stopped = false)
                return emptyList()
            }

            // 전이표 #2: 경로 재탐색으로 보고 취소
            current is State.Approaching && (tbt == null || tbt.turn != TurnType.RIGHT) -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #3: 멈추기 전에 적색이 아니게 됨. 멈출 의무가 사라졌으므로 기록 없이 끝낸다
            current is State.Approaching && !red && !current.stopped -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #4: 다가가는 중 멈춤
            current is State.Approaching && stoppedNow -> {
                state = current.copy(stopped = true)
                return emptyList()
            }

            // 전이표 #5: 판정 지점 안으로 들어옴. 회전이 끝나길 기다린다
            current is State.Approaching && tbt != null && tbt.distanceM <= CHECK_AT_M -> {
                state = State.Turning(
                    guided = current.guided,
                    stopped = current.stopped,
                    startHeading = frame.headingDeg,
                    movingMs = 0,
                    lastT = frame.t,
                )
                return emptyList()
            }

            // 전이표 #6: 안내 거리 안인데 아직 안 멈췄으면 한 번만 안내
            current is State.Approaching && tbt != null &&
                tbt.distanceM <= guideDistance(frame.speedKph) &&
                !current.stopped && !current.guided -> {
                state = current.copy(guided = true)
                return listOf(Guide(frame.t, RuleId.R3, GUIDE_TEXT, GuideAction.STOP))
            }

            // 전이표 #7: 회전을 시작하기 전 멈춤
            current is State.Turning && stoppedNow &&
                headingDiff(current.startHeading, frame.headingDeg) < TURN_START_DEG -> {
                state = current.copy(stopped = true)
                return emptyList()
            }

            // 전이표 #8: 회전이 끝남. 멈췄는지로 기록한다
            current is State.Turning &&
                headingDiff(current.startHeading, frame.headingDeg) >= TURN_DONE_DEG -> {
                state = State.Idle
                return listOf(
                    Event(
                        id = nextEventId(),
                        t = frame.t,
                        rule = RuleId.R3,
                        result = if (current.stopped) Result.KEPT else Result.MISSED,
                        guided = current.guided,
                        turn = TurnType.RIGHT,
                        lat = frame.lat,
                        lon = frame.lon,
                    ),
                )
            }

            // 전이표 #9: 주행했는데도 회전이 안 끝남. 회전하지 않은 것으로 보고 취소
            current is State.Turning && current.movingMs > TURN_TIMEOUT_MS -> {
                state = State.Idle
                return emptyList()
            }

            // 맞는 행이 없으면 상태를 유지한다
            else -> {
                state = current
                return emptyList()
            }
        }
    }

    /** spec 9.2: TURNING이면 전이표를 보기 전에 주행 시간을 먼저 갱신한다 (R1과 같다). */
    private fun updateMovingTime(state: State, frame: SignalFrame): State {
        if (state !is State.Turning) {
            return state
        }

        val elapsedMs = if (frame.speedKph > 0) frame.t - state.lastT else 0
        return state.copy(movingMs = state.movingMs + elapsedMs, lastT = frame.t)
    }

    private companion object {
        const val GUIDE_TEXT = "전방 적색이에요, 정지선에서 멈췄다 가세요"
    }
}
