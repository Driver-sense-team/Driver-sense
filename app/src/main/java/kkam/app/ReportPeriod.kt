package kkam.app

import kkam.engine.model.Event
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * 주행 리포트의 기간(일·월·연도) 계산. Android에 의존하지 않아서 JVM 테스트로 확인한다 (ReportPeriodTest).
 * 판정 이벤트의 t는 주행 프레임 시각이라 날짜가 아니다. 날짜는 앱이 저장할 때 붙인다 ([RecordStore]).
 *
 * 기록은 날짜별로 묶어 둔다: Map<날짜, 그날 판정 목록(나온 순서)>. 이 모양을 [DailyRecords]라고 부른다.
 */
typealias DailyRecords = Map<LocalDate, List<Event>>

/** 리포트를 묶어 보는 단위. 정차 화면 리포트 위쪽 전환 칩과 1:1이다. */
enum class ReportPeriod { DAY, MONTH, YEAR }

/** 날짜 구간 [start, endExclusive). 끝 날짜는 들어가지 않는다. */
data class DateSpan(val start: LocalDate, val endExclusive: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(endExclusive)
}

/** 날짜를 붙인 판정. 월·연도 리포트에서 판정마다 날짜를 보여주는 데 쓴다. */
data class DatedEvent(val date: LocalDate, val event: Event)

/** [anchor]가 들어 있는 기간. 일: 그날, 월: 그달 1일~말일, 연도: 1월 1일~12월 31일. */
fun ReportPeriod.spanOf(anchor: LocalDate): DateSpan = when (this) {
    ReportPeriod.DAY -> DateSpan(anchor, anchor.plusDays(1))
    ReportPeriod.MONTH -> anchor.withDayOfMonth(1).let { DateSpan(it, it.plusMonths(1)) }
    ReportPeriod.YEAR -> anchor.withDayOfYear(1).let { DateSpan(it, it.plusYears(1)) }
}

/** 이전(-1)·다음(+1) 기간으로 넘긴 기준 날짜. */
fun ReportPeriod.shift(anchor: LocalDate, steps: Long): LocalDate = when (this) {
    ReportPeriod.DAY -> anchor.plusDays(steps)
    ReportPeriod.MONTH -> anchor.plusMonths(steps)
    ReportPeriod.YEAR -> anchor.plusYears(steps)
}

/** 다음 기간으로 넘길 수 있는가. 오늘이 들어 있는 기간이 마지막이다 (미래는 볼 게 없다). */
fun ReportPeriod.canGoNext(anchor: LocalDate, today: LocalDate): Boolean =
    !spanOf(anchor).endExclusive.isAfter(today)

/** 이전 기간으로 넘길 수 있는가. 이 기간보다 앞선 날짜에 판정이 하나라도 있어야 한다 (빈 기간을 끝없이 넘기지 않게). */
fun ReportPeriod.canGoPrev(anchor: LocalDate, records: DailyRecords): Boolean {
    val start = spanOf(anchor).start
    return records.any { (date, events) -> date.isBefore(start) && events.isNotEmpty() }
}

/** 오늘이 들어 있는 기간을 보고 있는가. 빈 상태 문구("아직")에 쓴다. */
fun ReportPeriod.isCurrent(anchor: LocalDate, today: LocalDate): Boolean = today in spanOf(anchor)

/** 기간 안의 판정. 오래된 날짜가 먼저, 같은 날은 나온 순서. [buildReport]에 넘길 때는 [DatedEvent.event]만 쓴다. */
fun DailyRecords.eventsIn(span: DateSpan): List<DatedEvent> =
    filterKeys { it in span }
        .toSortedMap()
        .flatMap { (date, events) -> events.map { DatedEvent(date, it) } }

/**
 * 기간을 부르는 말. 제목("○○ 주행 리포트")과 아낀 범칙금 라벨에 쓴다.
 *
 * | 단위 | 오늘이 든 기간 | 바로 전 | 올해 안 | 올해 밖 |
 * | --- | --- | --- | --- | --- |
 * | 일 | 오늘 | 어제 | 11월 10일 | 2025년 11월 10일 |
 * | 월 | 이번 달 | 지난달 | 10월 | 2025년 10월 |
 * | 연도 | 올해 | 작년 | - | 2024년 |
 */
fun ReportPeriod.periodName(anchor: LocalDate, today: LocalDate): String = when (this) {
    ReportPeriod.DAY -> when (anchor) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> dayLabel(anchor, today)
    }
    ReportPeriod.MONTH -> when (anchor.withDayOfMonth(1)) {
        today.withDayOfMonth(1) -> "이번 달"
        today.withDayOfMonth(1).minusMonths(1) -> "지난달"
        else -> if (anchor.year == today.year) "${anchor.monthValue}월" else "${anchor.year}년 ${anchor.monthValue}월"
    }
    ReportPeriod.YEAR -> when (anchor.year) {
        today.year -> "올해"
        today.year - 1 -> "작년"
        else -> "${anchor.year}년"
    }
}

/** 리포트 제목. 예: "오늘 주행 리포트", "이번 달 주행 리포트", "2025년 주행 리포트" */
fun ReportPeriod.reportTitle(anchor: LocalDate, today: LocalDate): String = "${periodName(anchor, today)} 주행 리포트"

/**
 * 아낀 범칙금 카드 라벨. 예: "오늘 아낀 범칙금", "이번 달에 아낀 범칙금", "10월에 아낀 범칙금".
 * 오늘·어제·올해·작년은 그 자체가 때를 나타내는 말이라 "에"를 붙이지 않는다.
 */
fun ReportPeriod.savedLabel(anchor: LocalDate, today: LocalDate): String {
    val name = periodName(anchor, today)
    val particle = if (name in NO_PARTICLE) "" else "에"
    return "$name$particle 아낀 범칙금"
}

/**
 * 전환 칩 옆에 보이는 기간 이름 (절대 날짜). 제목이 "오늘"이어도 여기는 날짜를 보여 준다.
 * 예: 일 "11월 12일", 월 "2026년 11월", 연도 "2026년". 일은 올해가 아니면 연도를 붙인다.
 */
fun ReportPeriod.rangeLabel(anchor: LocalDate, today: LocalDate): String = when (this) {
    ReportPeriod.DAY -> dayLabel(anchor, today)
    ReportPeriod.MONTH -> "${anchor.year}년 ${anchor.monthValue}월"
    ReportPeriod.YEAR -> "${anchor.year}년"
}

/** 판정이 없을 때 문구. 지금 기간이면 "아직", 지난 기간이면 그냥 없다고 말한다. */
fun ReportPeriod.emptyLabel(anchor: LocalDate, today: LocalDate): String {
    val current = isCurrent(anchor, today)
    return when (this) {
        ReportPeriod.DAY -> if (current) "오늘은 아직 판정이 없어요" else "이날은 판정이 없어요"
        ReportPeriod.MONTH -> if (current) "이번 달은 아직 판정이 없어요" else "이달은 판정이 없어요"
        ReportPeriod.YEAR -> if (current) "올해는 아직 판정이 없어요" else "이해는 판정이 없어요"
    }
}

/**
 * 판정 기록 한 줄 왼쪽 시간 칸. 일 리포트는 시간만, 월·연도 리포트는 날짜를 윗줄에 붙인다 (칸 폭이 좁아 두 줄).
 * 시나리오의 t는 0부터 센 ms라 "42초", 실제 주행의 t는 현재 시각이라 "14:32".
 * 예: 일 "14:32", 월 "11/10\n14:32"
 */
fun ReportPeriod.itemTimeLabel(item: DatedEvent): String {
    val time = eventTimeLabel(item.event.t)
    return if (this == ReportPeriod.DAY) time else "${item.date.monthValue}/${item.date.dayOfMonth}\n$time"
}

/** 시나리오: "42초", 실제 주행: "14:32" */
fun eventTimeLabel(t: Long): String =
    if (t < EPOCH_START_MS) "${t / 1000}초" else CLOCK.format(Instant.ofEpochMilli(t))

private fun dayLabel(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) "${date.monthValue}월 ${date.dayOfMonth}일" else "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"

private val NO_PARTICLE = setOf("오늘", "어제", "올해", "작년")

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Seoul"))
