package kkam.engine

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.ExpectedOutput
import kkam.engine.model.Guide
import kkam.engine.model.OutputType

/**
 * 엔진 출력과 기대 출력을 spec 4.1 규칙으로 비교한다.
 * 비교 항목(type, t, rule, EVENT의 result·guided)만 같은 형식의 문장으로 만들고,
 * 두 문장이 같으면 일치로 본다.
 */
object ScenarioComparator {
    /** 다른 곳마다 메시지를 하나씩 돌려준다. 빈 리스트면 일치한다. */
    fun compare(expected: List<ExpectedOutput>, actual: List<EngineOutput>): List<String> {
        val differences = mutableListOf<String>()
        val count = maxOf(expected.size, actual.size)

        for (index in 0 until count) {
            val expectedText = describeExpected(expected.getOrNull(index))
            val actualText = describeActual(actual.getOrNull(index))

            if (expectedText != actualText) {
                differences += "${index + 1}번째 출력이 다름\n  기대: $expectedText\n  실제: $actualText"
            }
        }
        return differences
    }

    private fun describeExpected(output: ExpectedOutput?): String = when {
        output == null -> NONE
        output.type == OutputType.GUIDE -> "GUIDE t=${output.t} ${output.rule}"
        else -> "EVENT t=${output.t} ${output.rule} ${output.result} guided=${output.guided}"
    }

    private fun describeActual(output: EngineOutput?): String = when (output) {
        null -> NONE
        is Guide -> "GUIDE t=${output.t} ${output.rule}"
        is Event -> "EVENT t=${output.t} ${output.rule} ${output.result} guided=${output.guided}"
    }

    private const val NONE = "없음"
}
