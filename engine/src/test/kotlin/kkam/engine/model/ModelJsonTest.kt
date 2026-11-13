package kkam.engine.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

/** spec 2장의 예시 JSON을 그대로 파싱한다. */
class ModelJsonTest {
    @Test
    fun parsesSignalFrameExample() {
        val json = """
            {
              "t": 5000,
              "speedKph": 28.0,
              "gear": "D",
              "steeringDeg": -8.0,
              "turnSignal": "OFF",
              "brake": false,
              "lat": 37.5501,
              "lon": 127.0732,
              "headingDeg": 90.0,
              "tbt": { "turn": "LEFT", "distanceM": 48 },
              "light": null,
              "headlight": "OFF"
            }
        """.trimIndent()

        val frame = Json.decodeFromString<SignalFrame>(json)

        val expected = SignalFrame(
            t = 5000,
            speedKph = 28.0,
            gear = Gear.D,
            steeringDeg = -8.0,
            turnSignal = TurnSignal.OFF,
            brake = false,
            lat = 37.5501,
            lon = 127.0732,
            headingDeg = 90.0,
            tbt = Tbt(TurnType.LEFT, 48),
            light = null,
            headlight = HeadlightState.OFF,
        )
        assertEquals(expected, frame)
    }

    @Test
    fun frameWithoutHeadlightReadsAsNull() {
        // S1~S10처럼 headlight가 없는 프레임도 읽혀야 한다 (spec 2.1)
        val json = """
            {"t": 0, "speedKph": 40.0, "gear": "D", "steeringDeg": 0.0, "turnSignal": "OFF", "brake": false,
             "lat": 37.55, "lon": 127.07, "headingDeg": 90.0, "tbt": {"turn": "TUNNEL", "distanceM": 400}, "light": null}
        """.trimIndent()

        val frame = Json.decodeFromString<SignalFrame>(json)

        assertEquals(null, frame.headlight)
        assertEquals(TurnType.TUNNEL, frame.tbt?.turn)
    }

    @Test
    fun parsesGuideExampleByType() {
        val json = """{ "type": "GUIDE", "t": 5000, "rule": "R1", "text": "좌회전 곧이에요, 깜빡이 켜세요", "action": "SIGNAL_LEFT" }"""

        val output = Json.decodeFromString<EngineOutput>(json)

        assertEquals(Guide(5000, RuleId.R1, "좌회전 곧이에요, 깜빡이 켜세요", GuideAction.SIGNAL_LEFT), output)
    }

    @Test
    fun parsesEventExampleByType() {
        val json = """
            {
              "type": "EVENT",
              "id": "evt-1",
              "t": 11000,
              "rule": "R1",
              "result": "KEPT",
              "guided": false,
              "turn": "LEFT",
              "lat": 37.5503,
              "lon": 127.0712
            }
        """.trimIndent()

        val output = Json.decodeFromString<EngineOutput>(json)

        val expected = Event(
            id = "evt-1",
            t = 11000,
            rule = RuleId.R1,
            result = Result.KEPT,
            guided = false,
            turn = TurnType.LEFT,
            lat = 37.5503,
            lon = 127.0712,
        )
        assertEquals(expected, output)
    }

    @Test
    fun writesTypeFieldForServer() {
        val output: EngineOutput = Guide(5000, RuleId.R1, "안내", GuideAction.SIGNAL_LEFT)

        val json = Json.encodeToString(EngineOutput.serializer(), output)

        assertEquals("""{"type":"GUIDE","t":5000,"rule":"R1","text":"안내","action":"SIGNAL_LEFT"}""", json)
    }

    @Test
    fun eventTurnCanBeNull() {
        // R5 EVENT는 방향이 없어 turn이 null이다 (spec 2.3)
        val output: EngineOutput = Event("evt-1", 14000, RuleId.R5, Result.KEPT, true, null, 37.55, 127.07)

        val json = Json.encodeToString(EngineOutput.serializer(), output)

        assertEquals(output, Json.decodeFromString<EngineOutput>(json))
    }
}
