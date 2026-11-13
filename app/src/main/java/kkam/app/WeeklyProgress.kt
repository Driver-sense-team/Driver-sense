package kkam.app

import kotlin.math.roundToInt

/*
 * 교정 진행·주간 코칭 화면(기획서 UI-4, F6·F7)에 그릴 값.
 * Android에 의존하지 않아서 JVM 테스트로 확인한다 (WeeklyProgressTest).
 */

/** 습관 하나의 단계 (기획서 4.1: 진단 → 교정 → 유지 → 졸업). 진단은 습관을 고르기 전이라 여기엔 없다. */
enum class HabitStage(val label: String) {
    /** 고른 습관을 매번 안내한다. */
    CORRECT("교정"),

    /** 최근 10회 중 9회 이상 지킴. 안내를 두 번에 한 번으로 줄인다. */
    MAINTAIN("유지"),

    /** 유지를 3주 연속 이어감. 안내를 끄고 기록만 한다. */
    GRADUATED("졸업"),
}

/** 유지 판단에 보는 최근 판정 수 (기획서 4.1 "최근 10회"). */
const val RECENT_WINDOW = 10

/** 최근 [RECENT_WINDOW]회 중 이만큼 지키면 유지 (기획서 4.1 "9회 이상"). */
const val MAINTAIN_KEPT = 9

/** 유지를 이만큼 연속으로 이어가면 졸업 (기획서 4.1 "3주 연속"). */
const val GRADUATION_WEEKS = 3

/**
 * 졸업 단계를 정한다.
 * TODO(AI 담당 졸업 판정 P9 GuidancePolicy, 11/25): 엔진 결과가 나오면 이 함수 대신 엔진 값을 쓴다.
 *
 * | 조건                                          | 단계      |
 * |-----------------------------------------------|-----------|
 * | 유지 [GRADUATION_WEEKS]주 이상 연속            | GRADUATED |
 * | 판정이 [RECENT_WINDOW]회 이상이고 그중 [MAINTAIN_KEPT]회 이상 지킴 | MAINTAIN  |
 * | 그 밖 (판정이 10회 안 됨 포함)                  | CORRECT   |
 *
 * @param keptInRecent 최근 [RECENT_WINDOW]회 중 지킨 수
 * @param recentCount 최근 판정 수 ([RECENT_WINDOW]보다 크면 [RECENT_WINDOW]로 본다)
 * @param maintainedWeeks 유지 상태를 연속으로 이어간 주 수
 */
fun habitStage(keptInRecent: Int, recentCount: Int, maintainedWeeks: Int): HabitStage = when {
    maintainedWeeks >= GRADUATION_WEEKS -> HabitStage.GRADUATED
    recentCount >= RECENT_WINDOW && keptInRecent >= MAINTAIN_KEPT -> HabitStage.MAINTAIN
    else -> HabitStage.CORRECT
}

/** 습관 하나의 졸업 진행 상태. 습관 카드 한 장 = 값 하나. */
data class HabitProgress(
    val habit: Habit,
    /** 판정 결과, 오래된 것부터. true = 지킴. 마지막 [RECENT_WINDOW]개만 본다. */
    val results: List<Boolean>,
    /** 유지 상태를 연속으로 이어간 주 수. */
    val maintainedWeeks: Int = 0,
) {
    private val recent: List<Boolean> get() = results.takeLast(RECENT_WINDOW)

    val recentCount: Int get() = recent.size
    val keptInRecent: Int get() = recent.count { it }

    val stage: HabitStage get() = habitStage(keptInRecent, recentCount, maintainedWeeks)

    /**
     * 졸업까지 진행률 0~1. 막대를 [GRADUATION_WEEKS] + 1칸으로 나눈다.
     * 첫 칸 = 유지에 들어가기(지킨 수 / [MAINTAIN_KEPT]만큼 채움), 나머지 칸 = 유지 1주마다 한 칸.
     * 예: 교정 중 8번 지킴 → 0.22, 유지 1주 → 0.5, 졸업 → 1.
     */
    val progress: Float
        get() {
            val step = 1f / (GRADUATION_WEEKS + 1)
            return when (stage) {
                HabitStage.CORRECT -> step * keptInRecent.coerceAtMost(MAINTAIN_KEPT) / MAINTAIN_KEPT
                HabitStage.MAINTAIN -> step * (1 + maintainedWeeks)
                HabitStage.GRADUATED -> 1f
            }
        }

    /** 예: "최근 10번 중 9번 지킴" */
    val recentLabel: String get() = "최근 ${recentCount}번 중 ${keptInRecent}번 지킴"

    /**
     * 다음 단계까지 남은 것.
     * 교정: 유지까지 적어도 몇 번 더 지켜야 하는지 (지킨 수가 모자란 만큼, 판정 수가 모자란 만큼 중 큰 쪽).
     */
    val nextLabel: String
        get() = when (stage) {
            HabitStage.CORRECT -> {
                val more = maxOf(MAINTAIN_KEPT - keptInRecent, RECENT_WINDOW - recentCount, 1)
                "유지까지 ${more}번 더 지키기"
            }
            HabitStage.MAINTAIN -> "졸업까지 ${GRADUATION_WEEKS - maintainedWeeks}주 남음"
            HabitStage.GRADUATED -> "졸업! 이제 안내 없이 기록만 해요"
        }
}

/** 하루 준수율. 주행이 없던 날은 total = 0. */
data class DayRate(
    /** 예: "월" */
    val day: String,
    val kept: Int,
    val total: Int,
) {
    /** 0~100 정수 퍼센트. 주행이 없으면 null (막대를 그리지 않는다). */
    val percent: Int? get() = if (total == 0) null else percentOf(kept, total)
}

/** 자주 놓친 곳 한 줄. */
data class MissedPlace(
    /** 예: "역삼역 사거리 · 좌회전" */
    val name: String,
    val count: Int,
)

/** 교정 진행 화면 전체 값. */
data class WeeklyProgress(
    /** 예: "11월 2주 · 최근 7일" */
    val periodLabel: String,
    /** 교정 중인 습관 1~2개. 카드가 이 순서로 나온다. */
    val habits: List<HabitProgress>,
    /** 최근 7일, 오래된 날부터. 마지막이 오늘. */
    val days: List<DayRate>,
    /** 진단 기간 준수율. 이번 주와 비교한다. */
    val diagnosisPercent: Int,
    /** 많이 놓친 순서. 최대 [TOP_MISSED]개만 그린다. */
    val missedPlaces: List<MissedPlace>,
    /** 이번 주 안내한 횟수와 그중 안내 뒤 지킨 수 (안내 후 준수 전환율). */
    val guided: Int,
    val keptAfterGuide: Int,
    /** 안내 듣고 지켜서 아낀 범칙금 (원). */
    val savedWon: Int,
    /** AI 주간 코칭 3~4문장 (기획서 AI-1). */
    val coaching: String,
    /** 다음 주 집중 포인트 한 개. */
    val nextFocus: String,
) {
    /** 이번 주 전체 준수율 (주행한 날 판정 합). 판정이 없으면 0. */
    val weekPercent: Int get() = percentOf(days.sumOf { it.kept }, days.sumOf { it.total })

    /** 진단 대비 변화 (퍼센트포인트). 양수 = 좋아짐. */
    val changeFromDiagnosis: Int get() = weekPercent - diagnosisPercent

    /** 안내 후 준수 전환율. 안내가 없었으면 0. */
    val guideConversionPercent: Int get() = percentOf(keptAfterGuide, guided)

    val topMissed: List<MissedPlace> get() = missedPlaces.sortedByDescending { it.count }.take(TOP_MISSED)
}

/** 자주 놓친 곳은 3곳까지 (기획서 UI-4 "Top 3"). */
const val TOP_MISSED = 3

private fun percentOf(part: Int, whole: Int): Int = if (whole == 0) 0 else (part * 100.0 / whole).roundToInt()

/**
 * 교정 진행 값을 어디서 가져올지. 지금은 데모 고정값 하나뿐이다.
 * TODO(AI 담당 습관 인사이트 P11 12/1, 졸업 판정 P9 11/25): 판정 기록으로 계산하는 구현을 더하고 바꿔 끼운다.
 * TODO(AI 코칭 문장): Gleo 호출(앱) + AI 담당 주간 코칭 프롬프트(P10)로 [WeeklyProgress.coaching]·[WeeklyProgress.nextFocus]를 채운다.
 */
interface ProgressSource {
    fun load(): WeeklyProgress
}

/**
 * PT 데모용 고정값. 진단 리포트(DemoDiagnosisSource)의 "좌회전 깜빡이 62%"에서 이어진다.
 * 좌회전 깜빡이는 교정 중이고 이번 주 좋아지는 중, 적색 우회전 일시정지는 유지 1주째.
 */
object DemoProgressSource : ProgressSource {
    override fun load() = WeeklyProgress(
        periodLabel = "최근 7일 · 교정 2주째",
        habits = listOf(
            // 최근 10번 중 8번 지킴 → 교정. 유지까지 1번 더.
            HabitProgress(Habit.LEFT_SIGNAL, results = listOf(false, true, true, false, true, true, true, true, true, true)),
            // 10번 중 10번 지킴, 유지 1주째 → 졸업까지 2주.
            HabitProgress(Habit.RED_RIGHT_STOP, results = List(RECENT_WINDOW) { true }, maintainedWeeks = 1),
        ),
        days = listOf(
            DayRate("수", kept = 3, total = 5), // 60%
            DayRate("목", kept = 4, total = 6), // 67%
            DayRate("금", kept = 5, total = 6), // 83%
            DayRate("토", kept = 2, total = 3), // 67%
            DayRate("일", kept = 0, total = 0), // 주행 없음
            DayRate("월", kept = 5, total = 6), // 83%
            DayRate("화", kept = 4, total = 4), // 100% (오늘)
        ), // 합계 23 / 30 = 77%
        diagnosisPercent = 62,
        missedPlaces = listOf(
            MissedPlace("역삼역 사거리 · 좌회전", 3),
            MissedPlace("회사 앞 삼거리 · 좌회전", 2),
            MissedPlace("마트 앞 교차로 · 적색 우회전", 1),
        ),
        guided = 14,
        keptAfterGuide = 11, // 79%
        savedWon = 330_000, // 11번 × 좌회전 깜빡이 범칙금 3만 원
        coaching = "이번 주 준수율이 진단 때 62%에서 77%로 올랐어요. " +
            "안내를 들은 14번 중 11번은 바로 깜빡이를 켜셨어요. " +
            "다만 역삼역 사거리처럼 차로를 바꾸고 곧바로 좌회전하는 곳에서 자주 놓쳤어요. " +
            "적색 우회전 일시정지는 꾸준히 지키고 있어요.",
        nextFocus = "역삼역 사거리 앞에서는 차로를 바꾸기 전에 깜빡이부터 켜기",
    )
}
