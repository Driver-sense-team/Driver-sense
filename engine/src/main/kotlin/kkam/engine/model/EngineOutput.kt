package kkam.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 엔진 출력. JSON의 "type" 값으로 [Guide]와 [Event]를 구분한다.
 * "type"은 kotlinx.serialization이 sealed 타입에 기본으로 쓰는 필드 이름이고,
 * 값은 각 클래스의 @SerialName에서 온다.
 */
@Serializable
sealed interface EngineOutput {
    val t: Long
    val rule: RuleId
}

/** 안내 요청 (spec 2.2). 앱이 음성으로 읽고 코치 카드에 표시한다. */
@Serializable
@SerialName("GUIDE")
data class Guide(
    override val t: Long,
    override val rule: RuleId,
    val text: String,
    /** 안내가 운전자에게 바라는 동작. 앱이 따랐는지 프레임으로 확인할 때 쓴다. */
    val action: GuideAction,
) : EngineOutput

/** 판정 이벤트 (spec 2.3). */
@Serializable
@SerialName("EVENT")
data class Event(
    /** 엔진 인스턴스 안에서 순서대로 evt-1, evt-2, ... */
    val id: String,
    /** 이벤트가 나온 프레임의 시각. */
    override val t: Long,
    override val rule: RuleId,
    val result: Result,
    /** 이 판정 전에 GUIDE가 나갔는가. */
    val guided: Boolean,
    /** R1은 LEFT·RIGHT·UTURN, R2·R4는 LEFT 또는 RIGHT, R3는 RIGHT, R8은 ROUNDABOUT_ENTER·ROUNDABOUT_EXIT, R5·R6·R7은 null. */
    val turn: TurnType?,
    val lat: Double,
    val lon: Double,
) : EngineOutput

@Serializable
enum class RuleId { R1, R2, R3, R4, R5, R6, R7, R8 }

/** GUIDE가 바라는 동작 (spec 2.2). */
@Serializable
enum class GuideAction { SIGNAL_LEFT, SIGNAL_RIGHT, HEADLIGHT_ON, STOP, WIPER_ON, DEFOG }

@Serializable
enum class Result { KEPT, MISSED }
