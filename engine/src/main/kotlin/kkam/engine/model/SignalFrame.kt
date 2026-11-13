package kkam.engine.model

import kotlinx.serialization.Serializable

/** 차량 신호 한 프레임 (spec 2.1). */
@Serializable
data class SignalFrame(
    /** 시각 (ms). 시나리오는 0부터 시작한다. */
    val t: Long,
    val speedKph: Double,
    val gear: Gear,
    /** 핸들 각도. 좌우 부호는 미정 (spec 5장). SDK에서 안 나오면 null. */
    val steeringDeg: Double?,
    val turnSignal: TurnSignal,
    val brake: Boolean,
    val lat: Double,
    val lon: Double,
    /** 진행 방향. 북쪽 0°, 시계 방향, 0~360. */
    val headingDeg: Double,
    /** 내비의 다음 안내. 경로 안내 중이 아니면 null. */
    val tbt: Tbt?,
    /** 전방 차량 신호. 정보가 없으면 null. R3에서만 쓴다. */
    val light: TrafficLight?,
    /**
     * 전조등(하향등) 상태. R5에서만 쓴다.
     * 기본값 null: 이 필드가 없는 시나리오(S1~S10)도 그대로 읽히게 한다 (spec 2.1).
     */
    val headlight: HeadlightState? = null,
    /** 앞 유리 와이퍼 상태. R6에서만 쓴다. 기본값 null (spec 2.1). */
    val wiper: WiperMode? = null,
    /** 지금 비가 오는가. 시연은 시나리오 값, 실제 주행은 서버 날씨. 모르면 null. R6에서만 쓴다. */
    val rain: Boolean? = null,
    /** 앞유리 성에 제거가 켜져 있는가. R7에서만 쓴다. 기본값 null. */
    val defroster: Boolean? = null,
    /** 내기 순환이 켜져 있는가 (false면 외기). R7에서만 쓴다. 기본값 null. */
    val recirc: Boolean? = null,
    /** 운전자가 이 프레임에 "앞이 뿌옇다"고 말했는가. 말한 프레임만 true. R7에서만 쓴다. */
    val fogReported: Boolean? = null,
)

/** 내비의 다음 안내와 남은 거리. */
@Serializable
data class Tbt(
    val turn: TurnType,
    val distanceM: Int,
)

/** 전방 차량 신호. */
@Serializable
data class TrafficLight(
    val phase: LightPhase,
    /** 남은 시간 (초). 모르면 null. */
    val remainSec: Int?,
)

@Serializable
enum class Gear { P, R, N, D }

@Serializable
enum class TurnSignal { OFF, LEFT, RIGHT, HAZARD }

/** 내비 다음 안내의 종류 (spec 2.1). ROUNDABOUT_ENTER·ROUNDABOUT_EXIT는 회전교차로 진입·진출 (R8, spec 14장). */
@Serializable
enum class TurnType { LEFT, RIGHT, STRAIGHT, UTURN, RAMP_LEFT, RAMP_RIGHT, TUNNEL, ROUNDABOUT_ENTER, ROUNDABOUT_EXIT, OTHER }

/** 전조등 상태. AUTO는 스위치가 자동이라 필요하면 켜지는 상태 (spec 2.1 변환표). */
@Serializable
enum class HeadlightState { OFF, ON, AUTO }

/** 와이퍼 상태. AUTO는 스위치가 자동이라 비가 오면 움직이는 상태 (spec 2.1 변환표). */
@Serializable
enum class WiperMode { OFF, ON, AUTO }

@Serializable
enum class LightPhase { RED, YELLOW, GREEN, UNKNOWN }
