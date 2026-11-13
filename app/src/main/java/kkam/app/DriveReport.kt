package kkam.app

import kkam.engine.model.Event
import kkam.engine.model.LawEntry
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.TurnType

/** 기어 P에서 보여줄 오늘 주행 리포트 (기획서 UI-2). Android에 의존하지 않아서 JVM 테스트로 확인한다. */
data class DriveReport(
    val kept: Int,
    val missed: Int,
    /** 안내를 듣고 지켜서 아낀 범칙금 (원). */
    val savedWon: Int,
    /** 최근 판정이 먼저 온다. */
    val items: List<ReportItem>,
)

/** 판정 카드 한 장. */
data class ReportItem(
    val kept: Boolean,
    /** 예: "좌회전 깜빡이 지킴" */
    val title: String,
    /** 예: "안내 듣고 지켰어요" */
    val detail: String,
    /** 법 조항. 룰 테이블에 없으면 빈 문자열. */
    val article: String,
    /** 예: "단속됐다면: 승용차 범칙금 3만 원 ..." 룰 테이블에 없으면 빈 문자열. */
    val penalty: String,
)

/**
 * 오늘 판정과 법규 룰 테이블로 리포트를 만든다.
 * 아낀 범칙금 = 안내 후 지킨 판정 수 × 룰별 범칙금 (docs/tasks.md 단계 16과 같은 식).
 */
fun buildReport(events: List<Event>, laws: List<LawEntry>): DriveReport {
    val lawById = laws.associateBy { it.id }
    return DriveReport(
        kept = events.count { it.rule.inRate && it.result == Result.KEPT },
        missed = events.count { it.rule.inRate && it.result == Result.MISSED },
        savedWon = events.filter { it.guided && it.result == Result.KEPT }.sumOf { it.rule.fineWon() },
        items = events.reversed().map { it.toItem(it.lawId()?.let(lawById::get)) },
    )
}

/** 30000 → "3만 원", 0 → "0원" */
fun wonLabel(won: Int): String = when {
    won == 0 -> "0원"
    won % 10_000 == 0 -> "${won / 10_000}만 원"
    else -> "%,d원".format(won)
}

private fun Event.toItem(law: LawEntry?): ReportItem {
    val kept = result == Result.KEPT
    return ReportItem(
        kept = kept,
        title = "${title()} ${result.label()}",
        detail = when {
            !kept -> "놓쳤어요"
            guided -> "안내 듣고 지켰어요"
            else -> "안내 없이 지켰어요"
        },
        article = law?.article.orEmpty(),
        penalty = law?.penalty?.let { if (kept) "단속됐다면: $it" else "처분: $it" }.orEmpty(),
    )
}

/**
 * 룰별 범칙금 (승용차, 기획서 5.2). when이라 룰이 늘면 컴파일러가 빠진 룰을 알려준다.
 * R5(터널 전조등)는 처분 금액을 아직 확인하지 못해 0원으로 둔다 (spec 8장).
 */
private fun RuleId.fineWon(): Int = when (this) {
    RuleId.R1, RuleId.R2, RuleId.R4 -> 30_000
    RuleId.R3 -> 60_000
    RuleId.R5, RuleId.R6, RuleId.R7 -> 0
}

/**
 * 지킴률(준수율)에 세는 룰인가. R6(비 올 때 와이퍼)·R7(김서림)은 법규 근거가 없는 안전 안내라 기록만 남기고 세지 않는다 (spec 2.3).
 * 리포트 카드 목록에는 그대로 나온다.
 */
val RuleId.inRate: Boolean get() = this != RuleId.R6 && this != RuleId.R7

/**
 * 판정과 룰 테이블 행의 연결. 룰 테이블에 룰 필드가 생기면 그걸 쓴다.
 *
 * | 판정 | 룰 테이블 행 |
 * | --- | --- |
 * | R1 좌회전·우회전 | LAW-01 회전 전 방향지시등 |
 * | R1 유턴 | LAW-03 깜빡이 없이 회전 (LAW-01은 "좌회전 또는 우회전할 때"만 다룬다. spec 3장 법 근거) |
 * | R2 | LAW-03 깜빡이 없이 회전 |
 * | R3 | LAW-12 적색 신호 우회전 일시정지 |
 * | R4 | LAW-04 차로 변경 |
 * | R5·R6·R7 | 없음. R5(터널 전조등)는 룰 테이블에 행이 아직 없고, R6(와이퍼)·R7(김서림)은 법규 근거가 없다 |
 */
private fun Event.lawId(): String? = when (rule) {
    RuleId.R1 -> if (turn == TurnType.UTURN) "LAW-03" else "LAW-01"
    RuleId.R2 -> "LAW-03"
    RuleId.R3 -> "LAW-12"
    RuleId.R4 -> "LAW-04"
    RuleId.R5, RuleId.R6, RuleId.R7 -> null
}
