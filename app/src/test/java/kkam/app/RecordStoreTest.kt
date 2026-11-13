package kkam.app

import kkam.engine.model.Event
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.TurnType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordStoreTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val events = listOf(
        Event("evt-1", 11000, RuleId.R1, Result.KEPT, true, TurnType.LEFT, 37.55, 127.07),
        Event("evt-2", 25000, RuleId.R1, Result.MISSED, false, TurnType.RIGHT, 37.56, 127.08),
    )
    /** [events]를 엔진 형식 그대로 JSON 배열로. 손으로 저장 글자를 만들 때 쓴다. */
    private val eventsJson = Json.encodeToString(ListSerializer(Event.serializer()), events)

    @Test
    fun `날짜별 기록을 저장하고 그대로 되읽는다`() {
        val records = mapOf(today to events, today.minusDays(3) to events.take(1))

        assertEquals(records, RecordCodec.decode(RecordCodec.encode(records)))
    }

    @Test
    fun `판정이 없는 날은 저장하지 않는다`() {
        val text = RecordCodec.encode(mapOf(today to events, today.minusDays(1) to emptyList()))

        assertEquals(setOf(today), RecordCodec.decode(text).keys)
    }

    @Test
    fun `저장된 게 없거나 읽을 수 없으면 비운다`() {
        assertTrue(RecordCodec.decode(null).isEmpty())
        assertTrue(RecordCodec.decode("not json").isEmpty())
        assertTrue(RecordCodec.decode("[]").isEmpty())
        assertTrue(RecordCodec.decode("""{"days":[]}""").isEmpty())
    }

    @Test
    fun `읽을 수 없는 날만 빼고 나머지 날은 살린다`() {
        val text = """{"days":{"2026-11-10":$eventsJson,"어제":[],"2026-11-09":[{"type":"EVENT"}],"2026-11-08":"x"}}"""

        assertEquals(mapOf(today to events), RecordCodec.decode(text))
    }

    @Test
    fun `예전 하루치 저장 모양은 그 날짜의 기록으로 읽는다`() {
        val legacy = """{"date":"2026-11-09","events":$eventsJson}"""

        assertEquals(mapOf(LocalDate.of(2026, 11, 9) to events), RecordCodec.decodeLegacy(legacy))
    }

    @Test
    fun `예전 모양을 읽을 수 없으면 비운다`() {
        assertTrue(RecordCodec.decodeLegacy(null).isEmpty())
        assertTrue(RecordCodec.decodeLegacy("not json").isEmpty())
        assertTrue(RecordCodec.decodeLegacy("""{"date":"2026-11-10"}""").isEmpty())
        assertTrue(RecordCodec.decodeLegacy("""{"date":"어제","events":[]}""").isEmpty())
        assertTrue(RecordCodec.decodeLegacy("""{"date":"2026-11-10","events":[{"type":"EVENT"}]}""").isEmpty())
    }

    @Test
    fun `오늘 기록을 바꿔도 다른 날 기록은 남는다`() {
        val before = mapOf(today.minusDays(1) to events)

        val after = before.withDay(today, events.take(1))

        assertEquals(mapOf(today.minusDays(1) to events, today to events.take(1)), after)
        assertEquals(before, after.withDay(today, emptyList()))
    }

    @Test
    fun `작년 1월 1일보다 오래된 기록은 버린다`() {
        val records = mapOf(
            LocalDate.of(2024, 12, 31) to events,
            LocalDate.of(2025, 1, 1) to events,
            today to events,
        )

        assertEquals(setOf(LocalDate.of(2025, 1, 1), today), records.pruned(today).keys)
    }

    @Test
    fun `복원한 상태는 지킨 횟수와 기록 줄을 다시 센다`() {
        val state = restoredToday(events)

        assertEquals(1, state.kept)
        assertEquals(2, state.total)
        assertEquals(events, state.todayEvents)
        assertEquals(listOf("11초 좌회전 깜빡이 지킴", "25초 우회전 깜빡이 놓침"), state.logs)
    }

    @Test
    fun `복원한 뒤 새 주행을 시작해도 오늘 기록은 남는다`() {
        val state = restoredToday(events).resetDrive("S2")

        assertEquals(events, state.todayEvents)
        assertEquals(2, state.total)
    }
}
