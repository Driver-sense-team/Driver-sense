package kkam.engine

import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.SignalFrame
import kkam.engine.rules.R1Rule
import kkam.engine.rules.R2Rule
import kkam.engine.rules.R3Rule
import kkam.engine.rules.R4Rule
import kkam.engine.rules.R5Rule
import kkam.engine.rules.R6Rule
import kkam.engine.rules.R7Rule
import kkam.engine.rules.R8Rule

/** 판정 엔진. 차량 신호를 한 프레임씩 받아 룰에 넘기고 출력을 모아 돌려준다. */
class Engine {
    private var eventCount = 0
    private val r1Rule = R1Rule(::nextEventId)
    private val r2Rule = R2Rule(::nextEventId)
    private val r3Rule = R3Rule(::nextEventId)
    private val r4Rule = R4Rule(::nextEventId)
    private val r5Rule = R5Rule(::nextEventId)
    private val r6Rule = R6Rule(::nextEventId)
    private val r7Rule = R7Rule(::nextEventId)
    private val r8Rule = R8Rule(::nextEventId)

    /** 마지막으로 R1·R4·R8이 EVENT를 낸 프레임의 t. R2가 같은 회전을 두 번 세지 않게 쓴다 (spec 11.3 #2). */
    private var lastTurnRuleEventT: Long? = null

    /**
     * spec 2.4: 여러 룰이 한 프레임에 출력하면 룰 번호 순서로 담는다.
     * R2는 R1의 이 프레임 결과를 알아야 해서 R1 다음에, R7은 R6 상태를 알아야 해서 R6 다음에 부른다.
     * R4의 같은 프레임 EVENT는 나들목 300m 안이라는 조건(#2)이 이미 막는다.
     * R8은 맨 뒤라서 R2는 R8의 직전 프레임 상태(감시 중인가)를 본다. 회전교차로 안이면 R8이 감시 중이다.
     */
    fun onFrame(frame: SignalFrame): List<EngineOutput> {
        val r1 = r1Rule.onFrame(frame)
        if (r1.any { it is Event }) lastTurnRuleEventT = frame.t
        val r2 = r2Rule.onFrame(
            frame,
            R2Rule.OtherRules(r1Active = r1Rule.isActive || r8Rule.isActive, lastEventT = lastTurnRuleEventT),
        )
        val r3 = r3Rule.onFrame(frame)
        val r4 = r4Rule.onFrame(frame)
        if (r4.any { it is Event }) lastTurnRuleEventT = frame.t
        val r5 = r5Rule.onFrame(frame)
        val r6 = r6Rule.onFrame(frame)
        // R7은 R6 안내가 진행 중이면 기다린다. R6이 이 프레임을 처리한 뒤의 상태로 본다
        val r7 = r7Rule.onFrame(frame, r6Busy = r6Rule.isBusy)
        val r8 = r8Rule.onFrame(frame)
        if (r8.any { it is Event }) lastTurnRuleEventT = frame.t
        return r1 + r2 + r3 + r4 + r5 + r6 + r7 + r8
    }

    /** spec 2.3: 엔진 인스턴스 안에서 순서대로 evt-1, evt-2, ... */
    private fun nextEventId(): String {
        eventCount += 1
        return "evt-$eventCount"
    }
}
