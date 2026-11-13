package kkam.engine

import java.io.File
import kkam.engine.model.EngineOutput
import kkam.engine.model.Scenario
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/** scenarios 폴더의 JSON마다 테스트를 하나씩 만들어 실행한다. */
class ScenarioTest {
    @TestFactory
    fun scenarios(): List<DynamicTest> {
        val directory = File(System.getProperty("scenarios.dir"))
        val files = directory.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }

        // 폴더를 잘못 찾아 테스트 0개로 통과하는 일을 막는다.
        assertTrue(files.isNotEmpty(), "시나리오 파일이 없음: $directory")

        return files.map { file ->
            dynamicTest(file.nameWithoutExtension) { runScenario(file) }
        }
    }

    private fun runScenario(file: File) {
        val scenario = Json.decodeFromString<Scenario>(file.readText())

        val engine = Engine()
        val outputs = mutableListOf<EngineOutput>()
        for (frame in scenario.frames) {
            outputs += engine.onFrame(frame)
        }

        val differences = ScenarioComparator.compare(scenario.expect, outputs)
        if (differences.isNotEmpty()) {
            fail("${scenario.name}\n" + differences.joinToString("\n"))
        }
    }
}
