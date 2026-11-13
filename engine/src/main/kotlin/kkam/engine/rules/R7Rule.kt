package kkam.engine.rules

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame

// spec 13.1 파라미터. 10초는 시연용 초안이다 (spec 5장).
const val FOG_HOLD_MS = 10000L

/**
 * R7: 앞유리 김서림 (spec 13장).
 *
 * 차량은 김서림을 직접 알려주지 않으므로 "비 + 내기 순환 + 성에 제거 꺼짐"이 이어지거나
 * 운전자가 "앞이 뿌옇다"고 말하면 안내한다. 법규 근거가 없는 안전 안내라 KEPT만 기록한다.
 *
 * @param nextEventId 엔진 전체에서 순서대로 이벤트 id를 만들어주는 함수
 */
class R7Rule(private val nextEventId: () -> String) {
    private sealed class State {
        data object Idle : State()

        /** 김이 서리기 쉬운 상황이 이어져 시간을 재는 중 */
        data class Waiting(val since: Long) : State()

        /** 안내했고 운전자가 따르길 기다리는 중. 안내 때 내기 순환이 켜져 있었는지 기억한다 */
        data class Watching(val recircAtGuide: Boolean) : State()

        /** 이번 상황은 끝남 */
        data object Done : State()
    }

    private var state: State = State.Idle

    /**
     * 프레임 하나를 처리한다. spec 13.3 전이표를 위에서부터 검사해 처음 맞는 행만 적용한다.
     *
     * @param r6Busy R6(와이퍼) 안내가 진행 중이면 true. 이때는 상황 추정 안내를 시작하지 않는다
     */
    fun onFrame(frame: SignalFrame, r6Busy: Boolean): List<EngineOutput> {
        val current = state
        val foggy = frame.rain == true && frame.recirc == true && frame.defroster == false && frame.speedKph > 0

        when {
            // 전이표 #0: 주차·후진으로 보고 취소
            frame.gear == Gear.P || frame.gear == Gear.R -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #1: 운전자가 "앞이 뿌옇다"고 말함. 바로 안내한다
            current is State.Idle && frame.fogReported == true && frame.defroster == false -> {
                state = State.Watching(recircAtGuide = frame.recirc == true)
                return listOf(guide(frame))
            }

            // 전이표 #2: 김이 서리기 쉬운 상황이 시작됨
            current is State.Idle && foggy && !r6Busy -> {
                state = State.Waiting(since = frame.t)
                return emptyList()
            }

            // 전이표 #3: 상황이 풀렸거나 R6 안내가 시작됨
            current is State.Waiting && (!foggy || r6Busy) -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #4: 상황이 FOG_HOLD_MS 동안 이어짐. 한 번 안내한다
            current is State.Waiting && frame.t - current.since >= FOG_HOLD_MS -> {
                state = State.Watching(recircAtGuide = true)
                return listOf(guide(frame))
            }

            // 전이표 #5: 성에 제거를 켰거나, 켜져 있던 내기 순환을 외기로 바꿈
            current is State.Watching &&
                (frame.defroster == true || (current.recircAtGuide && frame.recirc == false)) -> {
                state = State.Done
                return listOf(
                    Event(
                        id = nextEventId(),
                        t = frame.t,
                        rule = RuleId.R7,
                        result = Result.KEPT,
                        guided = true,
                        turn = null,
                        lat = frame.lat,
                        lon = frame.lon,
                    ),
                )
            }

            // 전이표 #6: 비가 그치고 내기 순환도 꺼지면 다음 상황을 기다린다
            current is State.Done && frame.rain != true && frame.recirc != true -> {
                state = State.Idle
                return emptyList()
            }

            // 맞는 행이 없으면 상태를 유지한다 (WATCHING은 따르거나 P·R이 될 때까지 유지)
            else -> return emptyList()
        }
    }

    private fun guide(frame: SignalFrame) = Guide(frame.t, RuleId.R7, GUIDE_TEXT, GuideAction.DEFOG)

    private companion object {
        const val GUIDE_TEXT = "앞유리가 뿌예질 수 있어요, 성에 제거를 켜고 외기로 바꾸세요"
    }
}
