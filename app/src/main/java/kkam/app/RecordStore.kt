package kkam.app

import android.content.Context
import kkam.engine.model.Event
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * 날짜별 판정 기록을 JSON 글자로 바꾸고 되읽는다. Android에 의존하지 않아서 JVM 테스트로 확인한다.
 *
 * 지금 저장 모양: {"days":{"2026-11-10":[엔진 Event JSON, ...], "2026-11-12":[...]}}
 * 예전 저장 모양(하루치만): {"date":"2026-11-10","events":[...]}. 예전 모양은 [decodeLegacy]로 읽어 그 날짜의 기록으로 옮긴다.
 * Event는 엔진이 @Serializable로 공개하므로 형식을 새로 만들지 않는다.
 */
object RecordCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val eventList = ListSerializer(Event.serializer())

    /** 판정이 없는 날은 저장하지 않는다. */
    fun encode(records: DailyRecords): String = JsonObject(
        mapOf(
            KEY_DAYS to JsonObject(
                records.filterValues { it.isNotEmpty() }
                    .toSortedMap()
                    .map { (date, events) -> date.toString() to json.encodeToJsonElement(eventList, events) }
                    .toMap(),
            ),
        ),
    ).toString()

    /**
     * 날짜별 기록을 읽는다. 저장된 게 없거나 전체를 읽을 수 없으면 빈 기록.
     * 어느 하루만 읽을 수 없으면(엔진 형식이 바뀐 경우 등) 그날만 빼고 나머지는 살린다. 앱이 죽지 않게 한다.
     */
    fun decode(text: String?): DailyRecords {
        val days = parse(text)?.get(KEY_DAYS)?.let { asObjectOrNull(it) } ?: return emptyMap()
        return days.mapNotNull { (key, value) ->
            val date = dateOrNull(key) ?: return@mapNotNull null
            val events = eventsOrNull(value) ?: return@mapNotNull null
            if (events.isEmpty()) null else date to events
        }.toMap()
    }

    /** 예전 하루치 모양을 그 날짜의 기록으로 읽는다. 읽을 수 없으면 빈 기록. */
    fun decodeLegacy(text: String?): DailyRecords {
        val root = parse(text) ?: return emptyMap()
        val date = root[KEY_LEGACY_DATE]?.let { primitiveOrNull(it) }?.let { dateOrNull(it) } ?: return emptyMap()
        val events = root[KEY_LEGACY_EVENTS]?.let { eventsOrNull(it) } ?: return emptyMap()
        return if (events.isEmpty()) emptyMap() else mapOf(date to events)
    }

    private fun parse(text: String?): JsonObject? {
        if (text == null) return null
        return try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            null
        }
    }

    private fun eventsOrNull(element: JsonElement): List<Event>? = try {
        json.decodeFromJsonElement(eventList, element.jsonArray)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun asObjectOrNull(element: JsonElement): JsonObject? = try {
        element.jsonObject
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun primitiveOrNull(element: JsonElement): String? = try {
        element.jsonPrimitive.content
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun dateOrNull(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (e: DateTimeParseException) {
        null
    }

    private const val KEY_DAYS = "days"
    private const val KEY_LEGACY_DATE = "date"
    private const val KEY_LEGACY_EVENTS = "events"
}

/**
 * 오래된 기록을 버린다. 연도 리포트에서 올해와 작년을 볼 수 있게 작년 1월 1일부터 남긴다.
 * 서버 저장이 붙으면 오래된 기록은 서버에서 본다. 기기 저장 공간(SharedPreferences)이 끝없이 커지지 않게 하려는 것이다.
 */
fun DailyRecords.pruned(today: LocalDate): DailyRecords {
    val keepFrom = today.minusYears(KEEP_YEARS_BEFORE).withDayOfYear(1)
    return filter { (date, events) -> !date.isBefore(keepFrom) && events.isNotEmpty() }
}

/** 오늘 판정 목록으로 그날 기록을 바꾼다. 다른 날 기록은 그대로 둔다. */
fun DailyRecords.withDay(date: LocalDate, events: List<Event>): DailyRecords =
    if (events.isEmpty()) this - date else this + (date to events)

/** 올해 말고 몇 해 전까지 남길지. 1 = 작년까지. */
private const val KEEP_YEARS_BEFORE = 1L

/**
 * 날짜별 판정 기록을 SharedPreferences에 저장한다. 앱이나 서비스가 다시 켜져도 남고, 날짜가 바뀌어도 지난 기록은 남는다.
 * 판정 이벤트에는 날짜가 없어서 저장할 때 [today]를 붙인다 (엔진은 현재 시각을 쓰지 않는다).
 * [today]는 테스트·시연에서 날짜를 바꿀 수 있게 밖에서 받는다.
 */
class RecordStore(
    context: Context,
    val today: () -> LocalDate = { LocalDate.now(SEOUL) },
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 마지막으로 읽거나 쓴 날짜. 켜 둔 채로 자정을 넘겼는지 보는 데 쓴다. */
    private var loadedDate: LocalDate = today()

    /** 오늘 기록. 없으면 빈 목록. */
    fun load(): List<Event> {
        loadedDate = today()
        return history()[loadedDate].orEmpty()
    }

    /** 오늘 기록 전체를 덮어쓴다. 다른 날 기록은 그대로 둔다. */
    fun save(events: List<Event>) {
        loadedDate = today()
        val next = history().withDay(loadedDate, events).pruned(loadedDate)
        prefs.edit()
            .putString(KEY_HISTORY, RecordCodec.encode(next))
            .remove(KEY_LEGACY)
            .apply()
    }

    /**
     * 날짜별 기록 전체. 리포트의 일·월·연도 보기에 쓴다.
     * 새 모양이 아직 없으면 예전 하루치 기록을 그 날짜의 기록으로 읽는다. 다음 [save] 때 새 모양으로 옮겨진다.
     */
    fun history(): DailyRecords {
        val text = prefs.getString(KEY_HISTORY, null)
        return if (text != null) RecordCodec.decode(text) else RecordCodec.decodeLegacy(prefs.getString(KEY_LEGACY, null))
    }

    /** 마지막으로 읽거나 쓴 뒤 날짜가 바뀌었는가. */
    fun dayChanged(): Boolean = today() != loadedDate

    private companion object {
        const val PREFS = "kkam_records"
        const val KEY_HISTORY = "history"
        /** 예전(하루치만 저장) 키. 읽기만 하고, 새 모양으로 저장하면 지운다. */
        const val KEY_LEGACY = "today"
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
