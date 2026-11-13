package kkam.engine

import kkam.engine.model.Event
import kkam.engine.model.ExpectedOutput
import kkam.engine.model.Guide
import kkam.engine.model.GuideAction
import kkam.engine.model.OutputType
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.TurnType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 시나리오 러너 자체가 믿을 만한지 확인한다. 엔진이 비어 있어도 통과해야 한다. */
class ScenarioComparatorTest {
    private val expected = listOf(
        ExpectedOutput(OutputType.GUIDE, 5000, RuleId.R1),
        ExpectedOutput(OutputType.EVENT, 11000, RuleId.R1, Result.KEPT, guided = true),
    )
    private val guide = Guide(5000, RuleId.R1, "안내", GuideAction.SIGNAL_LEFT)
    private val event = Event("evt-1", 11000, RuleId.R1, Result.KEPT, true, TurnType.LEFT, 37.55, 127.07)

    @Test
    fun sameOutputsHaveNoDifference() {
        assertEquals(emptyList(), ScenarioComparator.compare(expected, listOf(guide, event)))
    }

    @Test
    fun ignoresIdTextAndPosition() {
        val actual = listOf(
            guide.copy(text = "다른 문구"),
            event.copy(id = "evt-9", lat = 0.0, lon = 0.0),
        )

        assertEquals(emptyList(), ScenarioComparator.compare(expected, actual))
    }

    @Test
    fun reportsDifferentResult() {
        val actual = listOf(guide, event.copy(result = Result.MISSED))

        val differences = ScenarioComparator.compare(expected, actual)

        assertEquals(1, differences.size)
        assertTrue(differences[0].startsWith("2번째 출력이 다름"))
        assertTrue("EVENT t=11000 R1 MISSED guided=true" in differences[0])
    }

    @Test
    fun reportsDifferentGuided() {
        val actual = listOf(guide, event.copy(guided = false))

        assertEquals(1, ScenarioComparator.compare(expected, actual).size)
    }

    @Test
    fun reportsDifferentTime() {
        val actual = listOf(guide.copy(t = 6000), event)

        assertEquals(1, ScenarioComparator.compare(expected, actual).size)
    }

    @Test
    fun reportsMissingOutput() {
        val differences = ScenarioComparator.compare(expected, listOf(guide))

        assertEquals(1, differences.size)
        assertTrue("실제: 없음" in differences[0])
    }

    @Test
    fun reportsExtraOutput() {
        val differences = ScenarioComparator.compare(expected, listOf(guide, event, event))

        assertEquals(1, differences.size)
        assertTrue("기대: 없음" in differences[0])
    }
}
