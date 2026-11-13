package kkam.app

import kotlin.test.Test
import kotlin.test.assertEquals

class DiagnosisTest {
    @Test
    fun `준수율은 반올림한 정수 퍼센트이고 판정이 없으면 0이다`() {
        assertEquals(62, SituationRate(Habit.LEFT_SIGNAL, kept = 13, total = 21).percent) // 61.9%
        assertEquals(0, SituationRate(Habit.LEFT_SIGNAL, kept = 0, total = 0).percent)
    }

    @Test
    fun `데모 진단의 핵심 문구는 기획서 PT 흐름 1과 같다`() {
        assertEquals("좌회전 깜빡이 62%, 전국 평균보다 낮아요", DemoDiagnosisSource.load().headline)
    }

    @Test
    fun `가장 낮은 상황이 평균보다 높으면 높다고 말한다`() {
        val diagnosis = Diagnosis("", listOf(SituationRate(Habit.RIGHT_SIGNAL, kept = 9, total = 10)))
        assertEquals("우회전 깜빡이 90%, 전국 평균보다 높아요", diagnosis.headline)
    }

    @Test
    fun `전국 평균보다 낮은 상황만 낮은 순서로 추천한다`() {
        val diagnosis = Diagnosis(
            "",
            listOf(
                SituationRate(Habit.LEFT_SIGNAL, kept = 7, total = 10), // 70%
                SituationRate(Habit.RIGHT_SIGNAL, kept = 9, total = 10), // 90%
                SituationRate(Habit.RED_RIGHT_STOP, kept = 5, total = 10), // 50%
            ),
        )
        assertEquals(listOf(Habit.RED_RIGHT_STOP, Habit.LEFT_SIGNAL), diagnosis.belowAverage.map { it.habit })
    }

    @Test
    fun `기록이 없으면 핵심 문구가 비었다고 말한다`() {
        assertEquals("아직 진단 기록이 없어요", Diagnosis("", emptyList()).headline)
    }
}
