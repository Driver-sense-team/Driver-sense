package kkam.engine.rules

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.HeadlightState
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnType

// spec 8.1 파라미터
const val TUNNEL_GUIDE_M = 200

/**
 * R5: 터널 진입 전 전조등 (spec 8장).
 *
 * 놓침(MISSED)은 내지 않는다. 터널 안내가 사라진 것이 통과인지 재탐색인지 구분할 수 없기 때문이다.
 *
 * @param nextEventId 엔진 전체에서 순서대로 이벤트 id를 만들어주는 함수
 */
class R5Rule(private val nextEventId: () -> String) {
    private enum class State {
        /** 대기 */
        IDLE,

        /** 안내를 했고 전조등이 켜지길 기다리는 중 */
        WATCHING,

        /** 이번 터널은 끝남. 같은 터널로 다시 안내하지 않으려고 기다리는 중 */
        DONE,
    }

    private var state = State.IDLE

    /** 프레임 하나를 처리한다. spec 8.3 전이표를 위에서부터 검사해 처음 맞는 행만 적용한다. */
    fun onFrame(frame: SignalFrame): List<EngineOutput> {
        val tunnelNear = isTunnelWithin(frame, TUNNEL_GUIDE_M)
        val lightOn = frame.headlight == HeadlightState.ON || frame.headlight == HeadlightState.AUTO

        when {
            // 전이표 #0: 주차·후진으로 보고 취소
            frame.gear == Gear.P || frame.gear == Gear.R -> {
                state = State.IDLE
                return emptyList()
            }

            // 전이표 #1: 터널이 가까운데 전조등이 꺼져 있으면 안내
            state == State.IDLE && tunnelNear && frame.headlight == HeadlightState.OFF -> {
                state = State.WATCHING
                return listOf(Guide(frame.t, RuleId.R5, GUIDE_TEXT, GuideAction.HEADLIGHT_ON))
            }

            // 전이표 #2: 이미 켜져 있으면 안내도 기록도 하지 않는다
            state == State.IDLE && tunnelNear && lightOn -> {
                state = State.DONE
                return emptyList()
            }

            // 전이표 #3: 안내 뒤 전조등을 켬
            state == State.WATCHING && lightOn -> {
                state = State.DONE
                return listOf(
                    Event(
                        id = nextEventId(),
                        t = frame.t,
                        rule = RuleId.R5,
                        result = Result.KEPT,
                        guided = true,
                        turn = null,
                        lat = frame.lat,
                        lon = frame.lon,
                    ),
                )
            }

            // 전이표 #4: 터널 안내가 사라짐. 기록 없이 끝낸다 (MVP 단순화)
            state == State.WATCHING && !isTunnel(frame) -> {
                state = State.IDLE
                return emptyList()
            }

            // 전이표 #5: 터널 안내가 사라지면 다음 터널을 기다린다
            state == State.DONE && !isTunnel(frame) -> {
                state = State.IDLE
                return emptyList()
            }

            // 맞는 행이 없으면 상태를 유지한다 (headlight가 null인 경우 포함)
            else -> return emptyList()
        }
    }

    private fun isTunnel(frame: SignalFrame): Boolean = frame.tbt?.turn == TurnType.TUNNEL

    private fun isTunnelWithin(frame: SignalFrame, meters: Int): Boolean {
        val tbt = frame.tbt ?: return false
        return tbt.turn == TurnType.TUNNEL && tbt.distanceM <= meters
    }

    private companion object {
        const val GUIDE_TEXT = "터널이 곧 나와요, 전조등 켜세요"
    }
}
