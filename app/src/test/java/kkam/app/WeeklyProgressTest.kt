package kkam.app

import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklyProgressTest {
    /** 지킨 수 [kept], 놓친 수 [missed]인 판정 기록. 놓친 것이 먼저(오래된 쪽). */
    private fun results(kept: Int, missed: Int): List<Boolean> = List(missed) { false } + List(kept) { true }

    @Test
    fun `최근 10번 중 8번이면 교정, 9번이면 유지`() {
        assertEquals(HabitStage.CORRECT, habitStage(keptInRecent = 8, recentCount = 10, maintainedWeeks = 0))
        assertEquals(HabitStage.MAINTAIN, habitStage(keptInRecent = 9, recentCount = 10, maintainedWeeks = 0))
        assertEquals(HabitStage.MAINTAIN, habitStage(keptInRecent = 10, recentCount = 10, maintainedWeeks = 0))
    }

    @Test
    fun `판정이 10번이 안 되면 다 지켜도 교정`() {
        assertEquals(HabitStage.CORRECT, habitStage(keptInRecent = 9, recentCount = 9, maintainedWeeks = 0))
    }

    @Test
    fun `유지 2주는 유지, 3주면 졸업`() {
        assertEquals(HabitStage.MAINTAIN, habitStage(keptInRecent = 9, recentCount = 10, maintainedWeeks = 2))
        assertEquals(HabitStage.GRADUATED, habitStage(keptInRecent = 9, recentCount = 10, maintainedWeeks = 3))
    }

    @Test
    fun `기록이 10번보다 길면 마지막 10번만 본다`() {
        // 앞의 놓침 5번은 창 밖. 마지막 10번은 다 지킴.
        val progress = HabitProgress(Habit.LEFT_SIGNAL, results(kept = 10, missed = 5))
        assertEquals(10, progress.recentCount)
        assertEquals(10, progress.keptInRecent)
        assertEquals(HabitStage.MAINTAIN, progress.stage)
        assertEquals("최근 10번 중 10번 지킴", progress.recentLabel)
    }

    @Test
    fun `진행률은 유지 진입 1칸과 유지 주마다 1칸`() {
        assertEquals(0.25f * 8 / 9, HabitProgress(Habit.LEFT_SIGNAL, results(kept = 8, missed = 2)).progress, 0.001f)
        assertEquals(0.25f, HabitProgress(Habit.LEFT_SIGNAL, results(kept = 9, missed = 1)).progress, 0.001f)
        assertEquals(0.75f, HabitProgress(Habit.LEFT_SIGNAL, results(kept = 9, missed = 1), maintainedWeeks = 2).progress, 0.001f)
        assertEquals(1f, HabitProgress(Habit.LEFT_SIGNAL, results(kept = 9, missed = 1), maintainedWeeks = 3).progress, 0.001f)
    }

    @Test
    fun `다음 단계 문구`() {
        assertEquals("유지까지 1번 더 지키기", HabitProgress(Habit.LEFT_SIGNAL, results(kept = 8, missed = 2)).nextLabel)
        assertEquals("유지까지 3번 더 지키기", HabitProgress(Habit.LEFT_SIGNAL, results(kept = 7, missed = 0)).nextLabel)
        assertEquals("졸업까지 2주 남음", HabitProgress(Habit.LEFT_SIGNAL, results(kept = 10, missed = 0), maintainedWeeks = 1).nextLabel)
    }

    @Test
    fun `주행이 없는 날은 퍼센트가 없다`() {
        assertEquals(null, DayRate("일", kept = 0, total = 0).percent)
        assertEquals(67, DayRate("목", kept = 4, total = 6).percent)
    }

    @Test
    fun `데모 값은 진단 62%에서 이번 주 77%로 오른다`() {
        val demo = DemoProgressSource.load()
        assertEquals(77, demo.weekPercent)
        assertEquals(15, demo.changeFromDiagnosis)
        assertEquals(79, demo.guideConversionPercent)
        assertEquals(HabitStage.CORRECT, demo.habits[0].stage)
        assertEquals(HabitStage.MAINTAIN, demo.habits[1].stage)
    }

    @Test
    fun `자주 놓친 곳은 많은 순서로 3곳까지`() {
        val demo = DemoProgressSource.load().copy(
            missedPlaces = listOf(MissedPlace("가", 1), MissedPlace("나", 4), MissedPlace("다", 2), MissedPlace("라", 3)),
        )
        assertEquals(listOf("나", "라", "다"), demo.topMissed.map { it.name })
    }
}
