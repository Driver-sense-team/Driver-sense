package kkam.app

import kkam.engine.Engine
import kkam.engine.model.EngineOutput
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.HeadlightState
import kkam.engine.model.LightPhase
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType
import kkam.engine.model.WiperMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteDemoTest {
    private val route = RouteDemo.parse(File("src/main/assets/${RouteDemo.ASSET}").readText())
    private val run = RouteDemo.build(route)
    private val outputs: List<Pair<Long, EngineOutput>> = Engine().let { engine ->
        run.frames.flatMap { f -> engine.onFrame(f).map { f.t to it } }
    }

    @Test
    fun `경로는 세종대학교에서 포티투닷 판교까지 약 32km, 코치 지점과 각본 장면이 주행 순서대로 있다`() {
        assertEquals("세종대학교", route.start)
        assertEquals("포티투닷 (42dot)", route.destination)
        assertTrue(run.plan.distanceM in 30000..34000, "${run.plan.distanceM}")
        assertEquals(
            listOf(
                RouteDemo.CueKind.LEFT,
                RouteDemo.CueKind.RIGHT,
                RouteDemo.CueKind.LANE_CHANGE,
                RouteDemo.CueKind.SUDDEN_STOP,
                RouteDemo.CueKind.RAIN,
                RouteDemo.CueKind.LANE_KEEP,
                RouteDemo.CueKind.LANE_WAIT,
                RouteDemo.CueKind.CURVE,
                RouteDemo.CueKind.TUNNEL,
                RouteDemo.CueKind.RAMP_RIGHT,
                RouteDemo.CueKind.EXIT_SLOW,
                RouteDemo.CueKind.PRE_LANE,
                RouteDemo.CueKind.RIGHT,
                RouteDemo.CueKind.BRAKE_EARLY,
                RouteDemo.CueKind.LEFT,
                RouteDemo.CueKind.UTURN,
                RouteDemo.CueKind.ROUNDABOUT_ENTER,
                RouteDemo.CueKind.ROUNDABOUT_EXIT,
            ),
            route.cues.map { it.kind },
        )
        // 차선 변경, 비, 각본 장면은 내비 안내가 아니라서 경로 안내 순서(미리보기 칩)에 없다
        assertEquals(
            listOf(
                TurnType.LEFT, TurnType.RIGHT, TurnType.TUNNEL, TurnType.RAMP_RIGHT, TurnType.RIGHT,
                TurnType.LEFT, TurnType.UTURN, TurnType.ROUNDABOUT_ENTER, TurnType.ROUNDABOUT_EXIT,
            ),
            run.plan.turns,
        )
        assertEquals(route.points, run.plan.path)
        // 미리보기의 예상 소요 시간: 자동차전용도로 60 km/h, 시내 25 km/h로 어림한 실제 주행 시간 (재생 시간이 아니다)
        assertTrue(run.plan.durationMin!! in 40..60, "${run.plan.durationMin}분")
        assertTrue(route.cues.zipWithNext().all { (a, b) -> a.point < b.point })
    }

    @Test
    fun `엔진에 넣으면 지점마다 기대한 안내와 판정이 순서대로 나온다`() {
        val shown = outputs.map { (_, out) ->
            when (out) {
                is Guide -> "GUIDE ${out.rule}"
                is Event -> "EVENT ${out.rule} ${out.result} guided=${out.guided}"
            }
        }
        assertEquals(
            listOf(
                // 광나루로 좌회전: 안내 듣고 켬 → 지킴
                "GUIDE R1",
                "EVENT R1 KEPT guided=true",
                // 능동로 적색 우회전: 깜빡이 안내 → 켬, 이어서 적색이 되어 정지 안내 → 정지선에서 멈춤 → 둘 다 지킴
                "GUIDE R1",
                "GUIDE R3",
                "EVENT R1 KEPT guided=true",
                "EVENT R3 KEPT guided=true",
                // 자동차전용도로 비 구간: 와이퍼 안내 → 켬 → 지킴, 이어서 김서림 안내 → 성에 제거 켬 → 지킴
                "GUIDE R6",
                "EVENT R6 KEPT guided=true",
                "GUIDE R7",
                "EVENT R7 KEPT guided=true",
                // 분당수서로 터널: 전조등 안내 → 켬 → 지킴
                "GUIDE R5",
                "EVENT R5 KEPT guided=true",
                // 서현로 방면 나들목: 안내 듣고 오른쪽 깜빡이 → 지킴
                "GUIDE R4",
                "EVENT R4 KEPT guided=true",
                // 서현로 우회전: 안내했지만 안 켬 → 놓침 (리포트의 놓침 1건)
                "GUIDE R1",
                "EVENT R1 MISSED guided=true",
                // 청계산로 좌회전: 안내를 듣고도 판정 지점(30m)을 지난 뒤에야 켬 → 놓침 (깜빡이 타이밍 장면)
                "GUIDE R1",
                "EVENT R1 MISSED guided=true",
                // 청계산로 유턴: 안내 듣고 왼쪽 깜빡이 → 지킴
                "GUIDE R1",
                "EVENT R1 KEPT guided=true",
                // 청계산로 회전교차로: 들어갈 때 왼쪽, 나갈 때 오른쪽. 둘 다 안내 듣고 켬 → 지킴
                "GUIDE R8",
                "EVENT R8 KEPT guided=true",
                "GUIDE R8",
                "EVENT R8 KEPT guided=true",
            ),
            shown,
        )
        assertTrue(outputs.none { it.second.rule == RuleId.R2 }, "빨리 감기·굽은 길에서 R2가 나오면 안 된다")
    }

    @Test
    fun `안내와 판정은 모두 실제 속도 구간에서 나온다`() {
        outputs.forEach { (t, out) -> assertTrue(t !in run.fastForward, "${out.rule} at $t") }
        // 판정 직전 프레임들도 실제 속도 (빨리 감기에서 바로 판정으로 뛰지 않는다)
        outputs.filter { it.second is Guide }.forEach { (t, out) ->
            assertTrue(t - RouteDemo.FRAME_MS !in run.fastForward, "${out.rule} 안내 바로 앞이 빨리 감기")
        }
    }

    @Test
    fun `5분 30초 안에 끝난다 - 미리보기, 주행, 도착 뒤 기어 P까지`() {
        val total = run.frames.last().t + RouteDemo.FRAME_MS
        // 안내 카드를 영상에서 읽을 수 있게 주황·파랑을 몇 초씩 보여주고, 유턴·회전교차로와 각본 장면 8개까지 지나서 5분 남짓이다
        assertTrue(total <= 330_000, "총 ${total / 1000}초")
        assertTrue(run.fastForward.isNotEmpty())
        // 빨리 감는 프레임은 30 km/h보다 빨라서 R2가 회전으로 세지 않는다
        run.frames.filter { it.t in run.fastForward }.forEach { assertTrue(it.speedKph > 30.0, "${it.t}") }
    }

    @Test
    fun `기어 P로 출발 전 미리보기를 보여주고 기어 P로 도착한다`() {
        val preview = run.frames.takeWhile { it.t < RouteDemo.PREVIEW_MS }
        assertTrue(preview.isNotEmpty() && preview.all { it.gear == Gear.P && it.speedKph == 0.0 })
        assertEquals(Gear.D, run.frames.first { it.t >= RouteDemo.PREVIEW_MS }.gear)
        assertEquals(Gear.P, run.frames.last().gear)
        assertEquals(null, run.frames.last().tbt)
        assertEquals(route.points.last(), LatLon(run.frames.last().lat, run.frames.last().lon))
        val arrive = run.frames.single { it.t == run.arriveAt }
        assertEquals(Gear.D, arrive.gear)
        assertEquals(null, arrive.tbt)
        assertTrue(run.frames.filter { it.t > run.arriveAt }.all { it.gear == Gear.P })
    }

    @Test
    fun `시간은 1초씩, 속도는 순항 속도를 넘지 않고, 실제 속도 구간의 남은 거리는 늘지 않는다`() {
        run.frames.zipWithNext().forEach { (a, b) -> assertEquals(RouteDemo.FRAME_MS, b.t - a.t) }
        assertTrue(run.frames.all { it.speedKph <= RouteDemo.EXPRESS_KPH })
        run.frames.zipWithNext()
            .filter { (a, b) -> a.gear == Gear.D && a.tbt != null && b.tbt != null && a.tbt?.turn == b.tbt?.turn }
            .forEach { (a, b) -> assertTrue(b.tbt!!.distanceM <= a.tbt!!.distanceM + 1, "${a.t}: ${a.tbt} → ${b.tbt}") }
    }

    @Test
    fun `적색 우회전 앞에서는 신호가 적색이고 정지선에서 멈춘다, 전조등은 터널 앞에서 켠다`() {
        val red = run.frames.filter { it.light?.phase == LightPhase.RED }
        assertTrue(red.isNotEmpty() && red.all { it.tbt?.turn == TurnType.RIGHT || it.tbt?.turn == TurnType.TUNNEL })
        assertTrue(red.count { it.gear == Gear.D && it.speedKph == 0.0 } >= RouteDemo.RED_STOP_FRAMES)
        val firstOn = run.frames.first { it.headlight == HeadlightState.ON }
        assertEquals(TurnType.TUNNEL, firstOn.tbt?.turn)
        assertTrue(firstOn.tbt!!.distanceM <= RouteDemo.HEADLIGHT_ON_M)
    }

    @Test
    fun `적색 우회전은 깜빡이 안내와 정지 안내를 다른 프레임에서 차례로 낸다 - 사이에 깜빡이를 켠다`() {
        val red = route.cues.single { it.redLight }
        assertEquals(RouteDemo.Driver.AFTER_GUIDE, red.driver)
        val r3 = outputs.single { it.second is Guide && it.second.rule == RuleId.R3 }.first
        val r1BeforeRed = outputs.last { it.second is Guide && it.second.rule == RuleId.R1 && it.first < r3 }.first
        assertEquals(TurnType.RIGHT, run.frames.single { it.t == r1BeforeRed }.tbt?.turn)
        // 깜빡이 안내를 3초 이상 읽고, 켠 뒤 "좋아요"도 보인 다음에 정지 안내가 나온다 (음성이 서로 끊지 않는다)
        assertTrue(r3 - r1BeforeRed >= 5_000, "R1 ${r1BeforeRed / 1000}s, R3 ${r3 / 1000}s")
        val signalOn = run.frames.first { it.t > r1BeforeRed && it.turnSignal == TurnSignal.RIGHT }.t
        assertTrue(signalOn - r1BeforeRed >= 3_000 && signalOn < r3, "깜빡이 ${signalOn / 1000}s")
        assertEquals(null, run.frames.single { it.t == signalOn }.light, "깜빡이를 켤 때는 아직 적색이 아니다")
    }

    @Test
    fun `도착 장면은 짧다 - 마지막 판정에서 기어 P까지 15초 안`() {
        val lastEvent = outputs.last { it.second is Event }.first
        val parked = run.frames.first { it.gear == Gear.P && it.t > run.arriveAt }.t
        assertTrue(parked - lastEvent <= 15_000, "마지막 판정 ${lastEvent / 1000}s → 기어 P ${parked / 1000}s")
        // 멈추기 전 마지막 몇 m를 5 km/h로 기어가지 않는다
        val crawl = run.frames.filter { it.t in lastEvent..run.arriveAt && it.speedKph in 0.1..RouteDemo.MIN_KPH }
        assertTrue(crawl.size <= 1, "기어가는 프레임 ${crawl.size}개")
    }

    @Test
    fun `회전교차로 안은 일정한 속도로 돌고, 진출 안내 뒤 몇 초 지나 오른쪽 깜빡이를 켠다`() {
        val exitGuide = outputs.last { it.second is Guide && it.second.rule == RuleId.R8 }.first
        val inside = run.frames.filter { it.tbt?.turn == TurnType.ROUNDABOUT_EXIT }
        assertTrue(inside.isNotEmpty() && inside.all { it.speedKph == RouteDemo.ROUNDABOUT_KPH }, inside.map { it.speedKph }.toString())
        val rightOn = inside.first { it.turnSignal == TurnSignal.RIGHT }.t
        assertTrue(rightOn - exitGuide >= 3_000, "진출 안내 ${exitGuide / 1000}s, 오른쪽 깜빡이 ${rightOn / 1000}s")
        // 유턴은 진행 방향이 거의 반대로 바뀐다 (R1이 회전을 마친 것으로 본다)
        val uturn = run.frames.filter { it.tbt?.turn == TurnType.UTURN && it.t !in run.fastForward }
        val after = run.frames.first { it.t > uturn.last().t }
        assertTrue(kkam.engine.rules.headingDiff(uturn.last().headingDeg, after.headingDeg) > 150)
    }

    @Test
    fun `각본 장면 8개(차선 변경 포함)가 주행 순서대로 뜨고, 서로·엔진 안내와 겹치지 않고, 빨리 감기 중이 아니다`() {
        assertEquals(
            listOf(
                RouteDemo.CueKind.LANE_CHANGE, RouteDemo.CueKind.SUDDEN_STOP, RouteDemo.CueKind.LANE_KEEP, RouteDemo.CueKind.LANE_WAIT,
                RouteDemo.CueKind.CURVE, RouteDemo.CueKind.EXIT_SLOW, RouteDemo.CueKind.PRE_LANE, RouteDemo.CueKind.BRAKE_EARLY,
            ),
            run.scriptCues.map { it.kind },
        )
        run.scriptCues.zipWithNext().forEach { (a, b) -> assertTrue(a.hideAt <= b.showAt, "${a.kind}와 ${b.kind}가 겹친다") }
        run.scriptCues.forEach { cue ->
            val shown = cue.showAt until cue.hideAt step RouteDemo.FRAME_MS
            assertTrue(shown.none { it in run.fastForward }, "${cue.kind} 카드가 빨리 감기 중에 뜬다")
            assertTrue(cue.hideAt - cue.showAt >= 5_000, "${cue.kind} 카드가 너무 짧다")
            assertTrue(outputs.none { (t, out) -> out is Guide && t in cue.showAt until cue.hideAt }, "${cue.kind} 카드와 엔진 안내가 겹친다")
            assertTrue(scriptSpeech(run.scriptCues, cue.showAt) != null && scriptPopup(run.scriptCues, cue.showAt)?.tone == PopupTone.WARN)
        }
    }

    @Test
    fun `각본 장면의 차 동작 - 급정거, 커브·진출로·회전 앞 감속, 깜빡이 3초 뒤 이동`() {
        fun at(t: Long) = run.frames.single { it.t == t }
        fun cue(kind: RouteDemo.CueKind) = run.scriptCues.single { it.kind == kind }
        // 급정거: 멈춘 프레임에 카드, 직전 1초 사이 15 km/h 넘게 줄었고 브레이크
        val stop = cue(RouteDemo.CueKind.SUDDEN_STOP)
        assertTrue(at(stop.showAt).brake && at(stop.showAt).speedKph <= 5.0)
        assertTrue(at(stop.showAt - RouteDemo.FRAME_MS - RouteDemo.FRAME_MS).speedKph - at(stop.showAt - RouteDemo.FRAME_MS).speedKph >= 15.0)
        // 커브: "좋아요" 때 60 이하, 카드 동안 속도가 늘지 않는다
        val curve = cue(RouteDemo.CueKind.CURVE)
        assertTrue(at(curve.okAt!!).speedKph <= RouteDemo.CURVE_KPH)
        assertTrue((curve.showAt until curve.okAt!! step RouteDemo.FRAME_MS).zipWithNext().all { (a, b) -> at(b).speedKph <= at(a).speedKph })
        // 진출로·회전 앞: 카드 동안 속도가 늘지 않는다 ("줄이세요" → 실제로 줄어든다)
        listOf(RouteDemo.CueKind.EXIT_SLOW, RouteDemo.CueKind.BRAKE_EARLY).map(::cue).forEach { c ->
            val ts = (c.showAt until c.hideAt step RouteDemo.FRAME_MS).toList()
            assertTrue(ts.zipWithNext().all { (a, b) -> at(b).speedKph <= at(a).speedKph }, "${c.kind}")
            assertTrue(at(ts.last()).speedKph < at(ts.first()).speedKph, "${c.kind}")
        }
        // 깜빡이 뒤 3초: 카드가 뜰 때 오른쪽 깜빡이를 켜고, 3초 넘게 기다린 뒤 "좋아요"(옮김)
        val wait = cue(RouteDemo.CueKind.LANE_WAIT)
        assertEquals(TurnSignal.RIGHT, at(wait.showAt).turnSignal)
        assertTrue(wait.okAt!! - wait.showAt >= 3_000)
        // 1차로·급정거는 "좋아요"가 없다
        assertEquals(null, cue(RouteDemo.CueKind.LANE_KEEP).okAt)
        assertEquals(null, stop.okAt)
    }

    @Test
    fun `차선 변경은 팝업만 - 오른쪽 깜빡이를 잠깐 켜고 판정은 없다`() {
        val cue = run.scriptCues.single { it.kind == RouteDemo.CueKind.LANE_CHANGE }
        val okAt = cue.okAt!!
        assertTrue(cue.showAt < okAt && okAt < cue.hideAt)
        // 어느 쪽 차로로 옮기는지 팝업 제목과 음성이 말한다
        assertTrue(cue.toRight)
        assertEquals("오른쪽 차로로", scriptPopup(run.scriptCues, cue.showAt)?.turnLabel)
        assertEquals("오른쪽 차로로 옮겨요, 오른쪽 깜빡이 켜세요", scriptSpeech(run.scriptCues, cue.showAt))
        val during = run.frames.filter { it.t >= okAt && it.t < okAt + RouteDemo.LANE_SIGNAL_MS }
        assertTrue(during.isNotEmpty() && during.all { it.turnSignal == TurnSignal.RIGHT && it.speedKph > 30.0 })
        assertTrue(outputs.none { (t, _) -> t in cue.showAt..cue.hideAt })
    }

    @Test
    fun `비 구간 - 와이퍼 안내 뒤 와이퍼를 켜고, 김서림 안내 뒤 성에 제거를 켠다`() {
        val rainy = run.frames.filter { it.rain == true }
        assertTrue(rainy.isNotEmpty())
        val start = rainy.first().t
        // 비는 한 번 와서 그친다 (구간 하나)
        assertEquals(RouteDemo.RAIN_END_AFTER_MS / RouteDemo.FRAME_MS, rainy.size.toLong())
        assertTrue(rainy.zipWithNext().all { (a, b) -> b.t - a.t == RouteDemo.FRAME_MS })
        // 비 구간은 모두 실제 속도이고 자동차전용도로 순항 속도로 달린다 (빨리 감기에 묻히지 않는다)
        assertTrue(rainy.all { it.t !in run.fastForward && it.gear == Gear.D && it.speedKph == RouteDemo.EXPRESS_KPH })
        // 안내 → 조작 순서
        val wiperGuide = outputs.single { it.second is Guide && it.second.rule == RuleId.R6 }.first
        val fogGuide = outputs.single { it.second is Guide && it.second.rule == RuleId.R7 }.first
        assertTrue(wiperGuide < start + RouteDemo.WIPER_ON_AFTER_MS, "와이퍼 안내가 와이퍼를 켜기 전에 나온다")
        assertTrue(start + RouteDemo.WIPER_ON_AFTER_MS < fogGuide, "김서림 안내는 와이퍼 안내가 끝난 뒤에 나온다")
        assertTrue(fogGuide < start + RouteDemo.DEFROST_ON_AFTER_MS, "김서림 안내가 성에 제거를 켜기 전에 나온다")
        // 비 밖에서는 와이퍼·성에 제거가 꺼져 있고 내기 순환은 늘 켜져 있다
        assertTrue(run.frames.filter { it.rain != true }.all { it.wiper == WiperMode.OFF && it.defroster == false })
        assertTrue(run.frames.all { it.recirc == true })
    }

    /** 보고용 이벤트 순서표. `./gradlew :app:testDebugUnitTest --tests "*RouteDemoTest*" -i`로 본다. */
    @Test
    fun `이벤트 순서표 출력`() {
        val byT = run.frames.associateBy { it.t }
        println("총 ${(run.frames.last().t + RouteDemo.FRAME_MS) / 1000}초, 빨리 감기 ${run.fastForward.size}프레임, 거리 ${run.plan.distanceM} m")
        run.scriptCues.forEach { println("t=${it.showAt / 1000}s 각본 ${it.kind} (좋아요 ${it.okAt?.div(1000)}s, 숨김 ${it.hideAt / 1000}s)") }
        outputs.forEach { (t, out) ->
            val f = byT.getValue(t)
            val what = when (out) {
                is Guide -> "GUIDE ${out.rule} \"${out.text}\""
                is Event -> "EVENT ${out.rule} ${out.result} guided=${out.guided}"
            }
            println("t=${t / 1000}s ${f.speedKph.toInt()}km/h tbt=${f.tbt} $what")
        }
    }
}
