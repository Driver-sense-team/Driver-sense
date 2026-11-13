package kkam.engine.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** data/law_table.json을 읽어 Q&A에 쓸 수 있는 상태인지 확인한다. */
class LawTableTest {
    private val table: List<LawEntry> = Json.decodeFromString(
        ListSerializer(LawEntry.serializer()),
        File(System.getProperty("law.table.path")).readText(),
    )

    private val allowedSources = setOf(
        "https://economist.co.kr/article/view/ecn202610150041",
        "https://www.heraldk.com/article/2026042323450053921",
        "https://brunch.co.kr/@raiope/27",
        "https://www.reportera.co.kr/car/left-turn-signal-legal-requirement-traffic-safety/",
        "https://www.law.go.kr/법령/도로교통법",
        "https://www.law.go.kr/법령/도로교통법시행령",
        "https://www.law.go.kr/법령/도로교통법시행규칙",
    )

    @Test
    fun staysSmallEnoughForPrompt() {
        assertTrue(table.size in 1..MAX_ROWS, "행 수: ${table.size}")
    }

    @Test
    fun idsAreUnique() {
        assertEquals(table.size, table.map { it.id }.toSet().size)
    }

    @Test
    fun requiredFieldsAreFilled() {
        for (entry in table) {
            val required = listOf(
                entry.id,
                entry.topic,
                entry.situation,
                entry.article,
                entry.summary,
                entry.penalty,
            )
            assertTrue(required.none { it.isBlank() }, "빈 필드: ${entry.id}")
        }
    }

    @Test
    fun sourcesAreApproved() {
        for (entry in table) {
            assertTrue(entry.sourceUrl in allowedSources, "출처 확인: ${entry.id}")
        }
    }

    @Test
    fun coversRulesThatAnswersMustCite() {
        val articles = table.map { it.article }

        assertTrue(articles.any { "제38조" in it })
        assertTrue(articles.any { "제5조" in it })
        assertTrue(table.any { it.disputed }, "해석이 갈리는 항목이 있어야 한다")
    }

    private companion object {
        const val MAX_ROWS = 20
    }
}
