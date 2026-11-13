package kkam.app

import kkam.engine.model.LawEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/*
 * 법규 Q&A (기획서 F8, AI-2). 질문을 받아 룰 테이블(data/law_table.json)에서 근거를 찾고 답을 만든다.
 * 안드로이드 의존성이 없어서 JVM 테스트(LawQnaTest)로 확인한다.
 *
 * 원칙 (AI-2)
 * - 답 문장은 근거 행의 필드(summary, article)로만 조립한다. 테이블에 없는 숫자·조항을 만들지 않는다.
 * - 근거가 없으면 "확인된 근거가 없어요".
 * - 해석이 갈리는 항목(disputed)은 화면에서 따로 표시해 단정하지 않는다.
 */

/** 질문 하나에 대한 답. */
data class QnaAnswer(
    val question: String,
    /** 화면에 크게 보이고 TTS로 읽을 2~3문장. */
    val text: String,
    /** 근거 룰. 첫 번째가 답 문장을 만든 주 근거. 근거가 없으면 빈 목록. */
    val sources: List<LawEntry>,
) {
    val hasBasis: Boolean get() = sources.isNotEmpty()

    /** 근거 중 해석이 갈리는 항목이 있으면 true. 화면에 "해석이 갈려요" 표시. */
    val disputed: Boolean get() = sources.any { it.disputed }
}

/** 질문 → 답. 지금은 [KeywordQnaAnswerer](오프라인), 나중에 Gleo AI 구현을 같은 자리에 넣는다. */
fun interface QnaAnswerer {
    fun answer(question: String): QnaAnswer
}

// TODO(Gleo AI SDK 확인 후 앱 담당, 프롬프트는 AI 담당 P10(ai/ 프롬프트 빌더)):
//  GleoQnaAnswerer(laws) : QnaAnswerer 를 만든다. 질문 + 룰 테이블 전체를 프롬프트로 보내고
//  "2~3문장 답 + 근거 룰 ID"를 받아 QnaAnswer로 바꾼다. 응답의 룰 ID가 테이블에 없으면 근거 없음으로 본다.
//  오프라인이거나 호출이 실패하면 KeywordQnaAnswerer로 대신 답한다.

/** 화면의 추천 질문 칩. 모두 룰 테이블에 근거가 있는 질문만 둔다 (LawQnaTest가 확인). */
val SUGGESTED_QUESTIONS = listOf(
    "왜 우회전할 때 멈춰야 해?",
    "깜빡이는 몇 m 전에 켜야 해?",
    "주변에 차가 없어도 깜빡이 켜야 해?",
    "좌회전 대기 중에도 깜빡이 켜야 해?",
    "깜빡이 안 켜면 벌점도 있어?",
)

const val NO_BASIS_TEXT = "확인된 근거가 없어요. 룰 테이블에 있는 법규만 답할 수 있어요."

/** law_table.json 글을 룰 목록으로 읽는다. 앱(MainActivity.loadLaws)과 테스트가 같이 쓴다. */
fun parseLawTable(json: String): List<LawEntry> =
    Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(LawEntry.serializer()), json)

/** 출처 URL을 사람이 읽는 이름으로. "https://www.law.go.kr/법령/..." → "국가법령정보센터". */
fun sourceName(url: String): String {
    val host = url.substringAfter("://").substringBefore("/").removePrefix("www.")
    return KNOWN_SOURCES[host] ?: host
}

private val KNOWN_SOURCES = mapOf("law.go.kr" to "국가법령정보센터")

/**
 * 오프라인 키워드 매칭.
 *
 * 1. 질문과 각 룰(topic + situation + summary)에서 [Concept]를 찾는다. 같은 뜻의 다른 말("깜빡이" = "방향지시등")을 한 개념으로 묶는다.
 * 2. 질문과 룰이 함께 가진 개념의 무게를 더해 점수를 낸다.
 * 3. 최고 점수가 [MIN_SCORE] 이상이면 그 점수의 룰을 테이블 순서대로 최대 [MAX_SOURCES]개 근거로 쓴다.
 *    "깜빡이"처럼 거의 모든 룰에 있는 말 하나만 겹치면 근거로 보지 않는다 (아무 룰이나 단정하지 않기 위해).
 */
class KeywordQnaAnswerer(private val laws: List<LawEntry>) : QnaAnswerer {

    override fun answer(question: String): QnaAnswer {
        val sources = findSources(question)
        if (sources.isEmpty()) return QnaAnswer(question, NO_BASIS_TEXT, emptyList())
        return QnaAnswer(question, composeText(sources.first()), sources)
    }

    /** 점수가 가장 높은 룰들. 근거가 없으면 빈 목록. */
    fun findSources(question: String): List<LawEntry> {
        val asked = conceptsIn(question)
        if (asked.isEmpty()) return emptyList()

        val scored = laws.map { law -> law to score(asked, conceptsIn(lawText(law))) }
        val best = scored.maxOf { it.second }
        if (best < MIN_SCORE) return emptyList()
        return scored.filter { it.second == best }.map { it.first }.take(MAX_SOURCES)
    }

    /**
     * 주 근거의 요약(앞 [MAX_SUMMARY_SENTENCES]문장) + 근거 조항 한 문장 = 2~3문장.
     * 둘 다 테이블 필드 그대로라 새 숫자·조항이 생기지 않는다. 나머지 내용은 근거 카드에서 본다.
     */
    private fun composeText(law: LawEntry): String {
        val summary = law.summary.split(SENTENCE_END).take(MAX_SUMMARY_SENTENCES).joinToString(" ")
        val articleSentence = if (law.article == UNCONFIRMED_ARTICLE) {
            "근거 조항은 아직 확인이 필요해요."
        } else {
            "${law.article}에 따른 내용이에요."
        }
        return "$summary $articleSentence"
    }

    private fun lawText(law: LawEntry) = "${law.topic} ${law.situation} ${law.summary}"

    private fun score(asked: Set<Concept>, inLaw: Set<Concept>): Int =
        asked.intersect(inLaw).sumOf { it.weight }

    private companion object {
        const val MIN_SCORE = 2
        const val MAX_SOURCES = 2
        const val MAX_SUMMARY_SENTENCES = 2
        const val UNCONFIRMED_ARTICLE = "확인 필요"
        /** 문장 끝(마침표 뒤 공백)에서 자른다. 마침표는 앞 문장에 남는다. */
        val SENTENCE_END = Regex("(?<=[.?!])\\s+")
    }
}

/** 글에 들어 있는 개념. 띄어쓰기 차이("몇 m"/"몇m")는 공백을 지우고 비교한다. */
private fun conceptsIn(text: String): Set<Concept> {
    val compact = text.replace(" ", "")
    return Concept.entries.filter { concept -> concept.words.any { it.replace(" ", "") in compact } }.toSet()
}

/**
 * 질문과 룰을 잇는 개념. [words] 중 하나라도 글에 있으면 그 개념이 있다고 본다.
 * [weight]: 거의 모든 룰에 나오는 개념(방향지시등)은 1, 상황을 좁혀 주는 개념은 2.
 */
private enum class Concept(val weight: Int, val words: List<String>) {
    TURN_SIGNAL(1, listOf("깜빡이", "깜박이", "방향지시등", "방향등", "깜빡")),
    DISTANCE(2, listOf("몇 m", "몇 미터", "미터", "거리", "m 이상", "얼마나 전", "앞선 지점")),
    RIGHT_TURN(2, listOf("우회전")),
    LEFT_TURN(2, listOf("좌회전")),
    STOP(2, listOf("멈춰", "멈추", "멈춘", "일시정지", "서야", "정지해")),
    HIGHWAY(2, listOf("고속도로")),
    LANE_CHANGE(2, listOf("차로 변경", "차선 변경", "차로를 바꿀", "차선 바꿀", "끼어들")),
    DEMERIT(2, listOf("벌점")),
    PUBLIC_REPORT(2, listOf("신고", "블랙박스")),
    CRACKDOWN(2, listOf("단속")),
    NO_CARS(2, listOf("차가 없", "차 없", "아무도 없", "한적")),
    WAITING(2, listOf("대기", "기다리", "기다릴")),
    DEPARTURE(2, listOf("출발")),
    PEDESTRIAN(2, listOf("보행자", "횡단보도", "사람이")),
    ACCIDENT(2, listOf("사고", "과실")),
    HISTORY(2, listOf("언제부터", "새로 생긴", "새로 생겼", "원래 있")),
}
