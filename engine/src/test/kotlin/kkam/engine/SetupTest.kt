package kkam.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 빌드, JUnit 5, 직렬화 플러그인이 함께 동작하는지 확인한다. */
class SetupTest {
    @Serializable
    private data class Sample(val name: String, val distanceM: Int)

    @Test
    fun jsonRoundTrip() {
        val original = Sample("LEFT", 48)

        val json = Json.encodeToString(original)

        assertEquals("""{"name":"LEFT","distanceM":48}""", json)
        assertEquals(original, Json.decodeFromString<Sample>(json))
    }
}
