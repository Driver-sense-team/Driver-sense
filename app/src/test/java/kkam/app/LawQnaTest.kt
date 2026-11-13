package kkam.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 실제 룰 테이블(data/law_table.json)로 키워드 Q&A를 확인한다. 테스트는 app/ 폴더에서 돈다. */
class LawQnaTest {
    private val laws = parseLawTable(File("../data/law_table.json").readText())
    private val answerer = KeywordQnaAnswerer(laws)

    private fun sourceIds(question: String) = answerer.answer(question).sources.map { it.id }

    @Test
    fun `추천 질문은 모두 근거가 있다`() {
        for (question in SUGGESTED_QUESTIONS) {
            assertTrue(answerer.answer(question).hasBasis, "근거 없음: $question")
        }
    }

    @Test
    fun `우회전 멈춤 질문은 적색 우회전 일시정지 룰이 주 근거다`() {
        assertEquals(listOf("LAW-12", "LAW-13"), sourceIds("왜 우회전할 때 멈춰야 해?"))
    }

    @Test
    fun `깜빡이 거리 질문은 일반도로 30m 룰이 주 근거고 고속도로 룰이 함께 나온다`() {
        assertEquals(listOf("LAW-01", "LAW-02"), sourceIds("깜빡이는 몇 m 전에 켜야 해?"))
        assertEquals(listOf("LAW-01", "LAW-02"), sourceIds("방향지시등 몇미터 전에 켜?"))
    }

    @Test
    fun `주변에 차가 없을 때 질문은 예외 없음 룰을 찾는다`() {
        assertEquals(listOf("LAW-09"), sourceIds("주변에 차가 없어도 깜빡이 켜야 해?"))
    }

    @Test
    fun `벌점 질문은 벌점 신설 예정 룰을 찾는다`() {
        assertEquals("LAW-06", sourceIds("깜빡이 안 켜면 벌점도 있어?").first())
    }

    @Test
    fun `해석이 갈리는 질문은 disputed로 표시해 단정하지 않는다`() {
        val answer = answerer.answer("좌회전 대기 중에도 깜빡이 켜야 해?")
        assertEquals("LAW-10", answer.sources.first().id)
        assertTrue(answer.disputed)
    }

    @Test
    fun `무관한 질문은 확인된 근거가 없다고 답한다`() {
        for (question in listOf("터널에서 전조등 꼭 켜야 해?", "오늘 날씨 어때?", "", "깜빡이 색깔 바꿔도 돼?")) {
            val answer = answerer.answer(question)
            assertFalse(answer.hasBasis, "근거가 나오면 안 됨: $question")
            assertTrue(answer.text.startsWith("확인된 근거가 없어요"))
        }
    }

    @Test
    fun `답에는 주 근거의 요약과 조항이 그대로 들어간다`() {
        val answer = answerer.answer("왜 우회전할 때 멈춰야 해?")
        val main = answer.sources.first()
        assertTrue(main.summary in answer.text)
        assertTrue("도로교통법 제5조" in answer.text, answer.text)
    }

    @Test
    fun `답은 3문장을 넘지 않는다`() {
        for (question in SUGGESTED_QUESTIONS) {
            val sentences = answerer.answer(question).text.split(Regex("(?<=[.?!])\\s+")).filter { it.isNotBlank() }
            assertTrue(sentences.size in 2..3, "$question → ${sentences.size}문장")
        }
    }

    @Test
    fun `답의 숫자는 모두 근거 행에 있는 숫자다`() {
        val number = Regex("\\d+")
        for (question in SUGGESTED_QUESTIONS) {
            val answer = answerer.answer(question)
            val allowed = answer.sources.joinToString(" ") { "${it.summary} ${it.article}" }
            for (n in number.findAll(answer.text).map { it.value }) {
                assertTrue(n in allowed, "$question → 근거에 없는 숫자 $n")
            }
        }
    }

    @Test
    fun `출처 이름은 법령 사이트면 국가법령정보센터, 그 밖은 도메인이다`() {
        assertEquals("국가법령정보센터", sourceName("https://www.law.go.kr/법령/도로교통법"))
        assertEquals("economist.co.kr", sourceName("https://economist.co.kr/article/view/ecn202610150041"))
    }
}
