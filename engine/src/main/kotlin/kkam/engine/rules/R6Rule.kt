package kkam.engine.rules

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.WiperMode

// spec 10.1 파라미터
const val RAIN_HOLD_MS = 5000L

/**
 * R6: 비 올 때 와이퍼 (spec 10장).
 *
 * 법규 근거가 없는 안전 안내다. 놓침(MISSED)은 내지 않고, 안내를 듣고 켜면 지킴(KEPT)만 기록한다.
 * rain이나 wiper가 null이면 아무것도 하지 않는다.
 *
 * @param nextEventId 엔진 전체에서 순서대로 이벤트 id를 만들어주는 함수
 */
class R6Rule(private val nextEventId: () -> String) {
    private sealed class State {
        data object Idle : State()

        /** 비가 오고 와이퍼가 꺼져 있어 시간을 재는 중 */
        data class Waiting(val rainSince: Long) : State()

        /** 안내했고 와이퍼가 켜지길 기다리는 중 */
        data object Watching : State()

        /** 이번 비에는 끝남 */
        data object Done : State()
    }

    private var state: State = State.Idle

    /** R6 안내가 진행 중(시간을 재거나 와이퍼를 기다림)이면 true. R7이 이때는 기다린다 (spec 13.3). */
    val isBusy: Boolean get() = state is State.Waiting || state is State.Watching

    /** 프레임 하나를 처리한다. spec 10.3 전이표를 위에서부터 검사해 처음 맞는 행만 적용한다. */
    fun onFrame(frame: SignalFrame): List<EngineOutput> {
        val current = state
        val raining = frame.rain == true
        val wiperOn = frame.wiper == WiperMode.ON || frame.wiper == WiperMode.AUTO

        when {
            // 전이표 #0: 주차·후진으로 보고 취소
            frame.gear == Gear.P || frame.gear == Gear.R -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #1: 주행 중 비가 오는데 와이퍼가 꺼져 있음. 시간을 재기 시작한다
            current is State.Idle && raining && frame.wiper == WiperMode.OFF && frame.speedKph > 0 -> {
                state = State.Waiting(rainSince = frame.t)
                return emptyList()
            }

            // 전이표 #2: 비가 그치거나 와이퍼가 꺼짐 상태가 아님
            current is State.Waiting && (!raining || frame.wiper != WiperMode.OFF) -> {
                state = State.Idle
                return emptyList()
            }

            // 전이표 #3: 비가 RAIN_HOLD_MS 동안 계속 옴. 한 번 안내한다
            current is State.Waiting && frame.t - current.rainSince >= RAIN_HOLD_MS -> {
                state = State.Watching
                return listOf(Guide(frame.t, RuleId.R6, GUIDE_TEXT, GuideAction.WIPER_ON))
            }

            // 전이표 #4: 안내 뒤 와이퍼를 켬
            current is State.Watching && wiperOn -> {
                state = State.Done
                return listOf(
                    Event(
                        id = nextEventId(),
                        t = frame.t,
                        rule = RuleId.R6,
                        result = Result.KEPT,
                        guided = true,
                        turn = null,
                        lat = frame.lat,
                        lon = frame.lon,
                    ),
                )
            }

            // 전이표 #5, #6: 비가 그치면 다음 비를 기다린다
            (current is State.Watching || current is State.Done) && !raining -> {
                state = State.Idle
                return emptyList()
            }

            // 맞는 행이 없으면 상태를 유지한다
            else -> return emptyList()
        }
    }

    private companion object {
        const val GUIDE_TEXT = "비가 와요, 와이퍼 켜세요"
    }
}
