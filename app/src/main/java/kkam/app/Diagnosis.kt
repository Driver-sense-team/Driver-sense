package kkam.app

import kotlin.math.roundToInt

/**
 * 진단 리포트와 교정 진행(기획서 UI-3·UI-4)에서 다루는 습관. 준수율 막대 한 줄, 습관 카드 한 장 = 값 하나.
 */
enum class Habit(val label: String) {
    LEFT_SIGNAL("좌회전 깜빡이"),
    RIGHT_SIGNAL("우회전 깜빡이"),
    RED_RIGHT_STOP("적색 우회전 일시정지"),
}

/** 상황 하나의 진단 기간 준수율. 예: 좌회전 깜빡이 21번 중 13번 지킴. */
data class SituationRate(val habit: Habit, val kept: Int, val total: Int) {
    /** 0~100 정수 퍼센트. 판정이 없으면 0. */
    val percent: Int get() = if (total == 0) 0 else (kept * 100.0 / total).roundToInt()
}

/** 진단 리포트 화면에 그릴 값 전체. Android에 의존하지 않아서 JVM 테스트로 확인한다. */
data class Diagnosis(
    /** 예: "지난 2주 · 주행 38번" */
    val periodLabel: String,
    val rates: List<SituationRate>,
    /** 전국 평균 준수율 (퍼센트). 2025 교통문화지수 교차로 좌회전 방향지시등 점등률. */
    val nationalAveragePercent: Double = NATIONAL_AVERAGE_PERCENT,
) {
    /** 전국 평균보다 낮은 상황. 낮은 순서. 습관 카드에서 "추천"으로 표시한다. */
    val belowAverage: List<SituationRate>
        get() = rates.filter { it.percent < nationalAveragePercent }.sortedBy { it.percent }

    /**
     * 화면 맨 위 핵심 문구. 가장 낮은 상황을 전국 평균과 비교한다.
     * 예: "좌회전 깜빡이 62%, 전국 평균보다 낮아요"
     */
    val headline: String
        get() {
            val lowest = rates.minByOrNull { it.percent } ?: return "아직 진단 기록이 없어요"
            val compare = if (lowest.percent < nationalAveragePercent) "전국 평균보다 낮아요" else "전국 평균보다 높아요"
            return "${lowest.habit.label} ${lowest.percent}%, $compare"
        }
}

/**
 * 진단 값을 어디서 가져올지. 지금은 데모 고정값 하나뿐이다.
 * TODO(AI 담당 습관 인사이트 라이브러리, 12/1): 진단 기간 판정 이벤트로 계산하는 구현을 더하고 MainActivity에서 바꿔 끼운다.
 */
interface DiagnosisSource {
    fun load(): Diagnosis
}

/** PT 데모용 고정값 (기획서 5.5 흐름 1: "좌회전 깜빡이 62%, 전국 평균보다 낮아요"). */
object DemoDiagnosisSource : DiagnosisSource {
    override fun load() = Diagnosis(
        periodLabel = "지난 2주 · 안내 없이 기록만 했어요",
        rates = listOf(
            SituationRate(Habit.LEFT_SIGNAL, kept = 13, total = 21), // 62%
            SituationRate(Habit.RIGHT_SIGNAL, kept = 17, total = 20), // 85%
            SituationRate(Habit.RED_RIGHT_STOP, kept = 7, total = 9), // 78%
        ),
    )
}

/** 전국 평균선 (기획서 2.1, UI-3). */
const val NATIONAL_AVERAGE_PERCENT = 75.7
