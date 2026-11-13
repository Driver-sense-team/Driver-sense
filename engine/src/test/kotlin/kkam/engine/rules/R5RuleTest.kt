package kkam.engine.rules

import kkam.engine.Engine
import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.HeadlightState
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TurnType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** 시나리오 S11이 다루지 않는 R5 전이표의 행과 출력 필드를 확인한다. */
class R5RuleTest {
    @Test
    fun guideAndEventFields() {
        val outputs = drive(
            frame(t = 0, distanceM = 190, headlight = HeadlightState.OFF),
            frame(t = 2000, distanceM = 150, headlight = HeadlightState.ON),
        )

        val guide = assertIs<Guide>(outputs[0])
        assertEquals("터널이 곧 나와요, 전조등 켜세요", guide.text)
        assertEquals(GuideAction.HEADLIGHT_ON, guide.action)
        val event = assertIs<Event>(outputs[1])
        assertEquals(RuleId.R5, event.rule)
        assertEquals(Result.KEPT, event.result)
        assertEquals(true, event.guided)
        assertNull(event.turn)
    }

    @Test
    fun autoSwitchCountsAsOn() {
        // 전이표 #2: 이미 AUTO면 안내도 기록도 없다
        val already = drive(frame(t = 0, distanceM = 150, headlight = HeadlightState.AUTO))
        // 전이표 #3: 안내 뒤 AUTO로 바꿔도 지킴
        val switched = drive(
            frame(t = 0, distanceM = 150, headlight = HeadlightState.OFF),
            frame(t = 1000, distanceM = 130, headlight = HeadlightState.AUTO),
        )

        assertEquals(emptyList(), already)
        assertIs<Event>(switched.last())
    }

    @Test
    fun alreadyOnStaysQuietUntilTunnelGuideEnds() {
        val outputs = drive(
            frame(t = 0, distanceM = 150, headlight = HeadlightState.ON),
            // DONE이라 전조등을 껐다 켜도 같은 터널로 다시 안내하지 않는다
            frame(t = 1000, distanceM = 130, headlight = HeadlightState.OFF),
            frame(t = 2000, distanceM = 110, headlight = HeadlightState.ON),
        )

        assertEquals(emptyList(), outputs)
    }

    @Test
    fun unknownHeadlightDoesNothing() {
        val outputs = drive(
            frame(t = 0, distanceM = 150, headlight = null),
            frame(t = 1000, distanceM = 100, headlight = null),
        )

        assertEquals(emptyList(), outputs)
    }

    @Test
    fun tunnelGuideDisappearingWhileWatchingRecordsNothing() {
        val outputs = drive(
            frame(t = 0, distanceM = 150, headlight = HeadlightState.OFF),
            // 전이표 #4: 통과인지 재탐색인지 모르므로 기록하지 않는다
            frame(t = 9000, turn = TurnType.STRAIGHT, distanceM = 800, headlight = HeadlightState.OFF),
            frame(t = 10000, turn = TurnType.STRAIGHT, distanceM = 780, headlight = HeadlightState.ON),
        )

        assertEquals(listOf("GUIDE"), outputs.map { it.typeName() })
    }

    @Test
    fun nextTunnelGetsItsOwnGuide() {
        val outputs = drive(
            frame(t = 0, distanceM = 150, headlight = HeadlightState.OFF),
            frame(t = 2000, distanceM = 110, headlight = HeadlightState.ON),
            // 전이표 #5: 터널을 지나 다른 안내로 바뀜
            frame(t = 10000, turn = TurnType.STRAIGHT, distanceM = 600, headlight = HeadlightState.OFF),
            frame(t = 20000, distanceM = 180, headlight = HeadlightState.OFF),
        )

        assertEquals(listOf("GUIDE", "EVENT", "GUIDE"), outputs.map { it.typeName() })
    }

    @Test
    fun parkingCancelsWatching() {
        val outputs = drive(
            frame(t = 0, distanceM = 150, headlight = HeadlightState.OFF),
            // 전이표 #0
            frame(t = 1000, distanceM = 150, headlight = HeadlightState.OFF, gear = Gear.P),
            frame(t = 2000, distanceM = 150, headlight = HeadlightState.ON, gear = Gear.P),
        )

        assertEquals(listOf("GUIDE"), outputs.map { it.typeName() })
    }

    @Test
    fun farTunnelIsNotGuided() {
        val outputs = drive(frame(t = 0, distanceM = 201, headlight = HeadlightState.OFF))

        assertEquals(emptyList(), outputs)
    }

    private fun EngineOutput.typeName() = if (this is Guide) "GUIDE" else "EVENT"

    private fun drive(vararg frames: SignalFrame) =
        Engine().let { engine -> frames.flatMap { engine.onFrame(it) } }

    private fun frame(
        t: Long,
        distanceM: Int,
        headlight: HeadlightState?,
        turn: TurnType = TurnType.TUNNEL,
        gear: Gear = Gear.D,
    ) = SignalFrame(
        t = t,
        speedKph = if (gear == Gear.P) 0.0 else 70.0,
        gear = gear,
        steeringDeg = 0.0,
        turnSignal = kkam.engine.model.TurnSignal.OFF,
        brake = false,
        lat = 37.55,
        lon = 127.07,
        headingDeg = 90.0,
        tbt = Tbt(turn, distanceM),
        light = null,
        headlight = headlight,
    )
}
