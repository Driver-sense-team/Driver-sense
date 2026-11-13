package kkam.engine.model

import kotlinx.serialization.Serializable

/**
 * 시나리오 파일 한 개 (spec 4.1). 테스트와 PT 데모가 함께 쓰므로 main에 둔다.
 * 파일을 읽는 일은 하지 않는다. 읽기는 테스트나 앱이 맡는다.
 */
@Serializable
data class Scenario(
    val name: String,
    val description: String,
    /** 엔진이 내보내야 하는 출력 전체, 순서대로. */
    val expect: List<ExpectedOutput>,
    val frames: List<SignalFrame>,
)

/** 기대 출력 한 개. id, text, 위치는 비교하지 않으므로 필드가 없다. */
@Serializable
data class ExpectedOutput(
    val type: OutputType,
    val t: Long,
    val rule: RuleId,
    /** EVENT일 때만 적는다. */
    val result: Result? = null,
    /** EVENT일 때만 적는다. */
    val guided: Boolean? = null,
)

@Serializable
enum class OutputType { GUIDE, EVENT }
