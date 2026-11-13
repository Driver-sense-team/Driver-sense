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
    fun `경로는 세종대학교에서 포티투닷 판교까지 약 25km, 코치 지점은 좌·적색 우·차선·터널·나들목·우 순서다`() {
        assertEquals("세종대학교", route.start)
        assertEquals("포티투닷 (42dot)", route.destination)
        assertTrue(run.plan.distanceM in 24000..27000, "${run.plan.distanceM}")
        assertEquals(
            listOf(
                RouteDemo.CueKind.LEFT,
                RouteDemo.CueKind.RIGHT,
                RouteDemo.CueKind.LANE_CHANGE,
                RouteDemo.CueKind.TUNNEL,
                RouteDemo.CueKind.RAMP_RIGHT,
                RouteDemo.CueKind.RIGHT,
            ),
            route.cues.map { it.kind },
        )
        // 차선 변경은 내비 안내가 아니라서 경로 안내 순서(미리보기 칩)에 없다
        assertEquals(listOf(TurnType.LEFT, TurnType.RIGHT, TurnType.TUNNEL, TurnType.RAMP_RIGHT, TurnType.RIGHT), run.plan.turns)
        assertEquals(route.points, run.plan.path)
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
                // 능동로 적색 우회전: 깜빡이는 미리 켬(R1 안내 없이 지킴), 정지 안내 → 정지선에서 멈춤 → 지킴
                "GUIDE R3",
                "EVENT R1 KEPT guided=false",
                "EVENT R3 KEPT guided=true",
                // 분당수서로 터널: 전조등 안내 → 켬 → 지킴
                "GUIDE R5",
                "EVENT R5 KEPT guided=true",
                // 서현로 방면 나들목: 안내 듣고 오른쪽 깜빡이 → 지킴
                "GUIDE R4",
                "EVENT R4 KEPT guided=true",
                // 서현로 우회전: 안내했지만 안 켬 → 놓침 (리포트의 놓침 1건)
                "GUIDE R1",
                "EVENT R1 MISSED guided=true",
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
    fun `3분 안에 끝난다 - 미리보기, 주행, 도착 뒤 기어 P까지`() {
        val total = run.frames.last().t + RouteDemo.FRAME_MS
        assertTrue(total <= 180_000, "총 ${total / 1000}초")
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
    fun `차선 변경은 팝업만 - 오른쪽 깜빡이를 잠깐 켜고 판정은 없다`() {
        assertEquals(1, run.laneCues.size)
        val cue = run.laneCues.single()
        assertTrue(cue.showAt < cue.okAt && cue.okAt < cue.hideAt)
        val during = run.frames.filter { it.t >= cue.okAt && it.t < cue.okAt + RouteDemo.LANE_SIGNAL_MS }
        assertTrue(during.isNotEmpty() && during.all { it.turnSignal == TurnSignal.RIGHT && it.speedKph > 30.0 })
        assertTrue(outputs.none { (t, _) -> t in cue.showAt..cue.hideAt })
    }

    /** 보고용 이벤트 순서표. `./gradlew :app:testDebugUnitTest --tests "*RouteDemoTest*" -i`로 본다. */
    @Test
    fun `이벤트 순서표 출력`() {
        val byT = run.frames.associateBy { it.t }
        println("총 ${(run.frames.last().t + RouteDemo.FRAME_MS) / 1000}초, 빨리 감기 ${run.fastForward.size}프레임, 거리 ${run.plan.distanceM} m")
        run.laneCues.forEach { println("t=${it.showAt / 1000}s LANE_CHANGE 팝업 (깜빡이 ${it.okAt / 1000}s)") }
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
