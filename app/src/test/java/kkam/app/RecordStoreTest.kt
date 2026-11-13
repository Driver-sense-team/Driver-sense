package kkam.app

import kkam.engine.model.Event
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.TurnType
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

    @Test
    fun `같은 날이면 저장한 판정을 그대로 되읽는다`() {
        val text = RecordCodec.encode(today, events)

        assertEquals(events, RecordCodec.decode(text, today))
    }

    @Test
    fun `날짜가 바뀌었으면 비운다`() {
        val text = RecordCodec.encode(today, events)

        assertTrue(RecordCodec.decode(text, today.plusDays(1)).isEmpty())
    }

    @Test
    fun `저장된 게 없거나 읽을 수 없으면 비운다`() {
        assertTrue(RecordCodec.decode(null, today).isEmpty())
        assertTrue(RecordCodec.decode("not json", today).isEmpty())
        assertTrue(RecordCodec.decode("""{"date":"2026-11-10"}""", today).isEmpty())
        assertTrue(RecordCodec.decode("""{"date":"어제","events":[]}""", today).isEmpty())
        assertTrue(RecordCodec.decode("""{"date":"2026-11-10","events":[{"type":"EVENT"}]}""", today).isEmpty())
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
