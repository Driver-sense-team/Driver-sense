package kkam.app

import android.content.Context
import kkam.engine.model.Event
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * 오늘 판정 기록을 JSON 글자로 바꾸고 되읽는다. Android에 의존하지 않아서 JVM 테스트로 확인한다.
 * 저장 모양: {"date":"2026-11-10","events":[엔진 Event JSON, ...]}
 * Event는 엔진이 @Serializable로 공개하므로 형식을 새로 만들지 않는다.
 */
object RecordCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val eventList = ListSerializer(Event.serializer())

    fun encode(date: LocalDate, events: List<Event>): String = JsonObject(
        mapOf(
            KEY_DATE to JsonPrimitive(date.toString()),
            KEY_EVENTS to json.encodeToJsonElement(eventList, events),
        ),
    ).toString()

    /**
     * 저장된 날짜가 [today]이면 기록을, 아니면 빈 목록을 돌려준다.
     * 저장된 게 없거나 읽을 수 없는 모양(엔진 형식이 바뀐 경우 등)이어도 빈 목록. 앱이 죽지 않게 한다.
     */
    fun decode(text: String?, today: LocalDate): List<Event> {
        if (text == null) return emptyList()
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val date = LocalDate.parse(root.getValue(KEY_DATE).jsonPrimitive.content)
            if (date != today) return emptyList()
            json.decodeFromJsonElement(eventList, root.getValue(KEY_EVENTS).jsonArray)
        } catch (e: SerializationException) {
            emptyList()
        } catch (e: IllegalArgumentException) {
            emptyList()
        } catch (e: NoSuchElementException) {
            emptyList()
        } catch (e: DateTimeParseException) {
            emptyList()
        }
    }

    private const val KEY_DATE = "date"
    private const val KEY_EVENTS = "events"
}

/**
 * 오늘 판정 기록을 SharedPreferences에 저장한다. 앱이나 서비스가 다시 켜져도 오늘 기록이 남는다.
 * 날짜가 바뀌면 지난 기록은 버린다 (서버 저장 전까지는 오늘 것만 둔다).
 * [today]는 테스트·시연에서 날짜를 바꿀 수 있게 밖에서 받는다.
 */
class RecordStore(
    context: Context,
    private val today: () -> LocalDate = { LocalDate.now(SEOUL) },
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 마지막으로 읽거나 쓴 날짜. 켜 둔 채로 자정을 넘겼는지 보는 데 쓴다. */
    private var loadedDate: LocalDate = today()

    /** 오늘 기록. 날짜가 다르거나 없으면 빈 목록. */
    fun load(): List<Event> {
        loadedDate = today()
        return RecordCodec.decode(prefs.getString(KEY_RECORDS, null), loadedDate)
    }

    /** 오늘 기록 전체를 덮어쓴다. */
    fun save(events: List<Event>) {
        loadedDate = today()
        prefs.edit().putString(KEY_RECORDS, RecordCodec.encode(loadedDate, events)).apply()
    }

    /** 마지막으로 읽거나 쓴 뒤 날짜가 바뀌었는가. */
    fun dayChanged(): Boolean = today() != loadedDate

    private companion object {
        const val PREFS = "kkam_records"
        const val KEY_RECORDS = "today"
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
