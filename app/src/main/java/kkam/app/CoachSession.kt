package kkam.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import kkam.engine.Engine
import kkam.engine.model.EngineOutput
import kkam.engine.model.Guide
import kkam.engine.model.SignalFrame

/**
 * 주행 한 번의 흐름: 프레임(시나리오 재생 또는 실제 차량 신호) → 엔진 → 음성 → [CoachState].
 * 화면이 보이지 않아도 돌아야 해서 [CoachService]가 갖고, 화면은 [Listener]로 받아 그리기만 한다.
 * 모두 메인 스레드에서 돈다 (재생기와 수집기가 메인 스레드로 프레임을 넘긴다).
 */
class CoachSession(context: Context) {
    /** 상태가 바뀌면 불린다. 새 프레임 때문이면 frame이 있다. */
    fun interface Listener {
        fun onSessionChanged(frame: SignalFrame?)
    }

    private val player = ScenarioPlayer(context)
    private val collector = SignalCollector(context)
    private val speaker = Speaker(context).apply { start() }
    private val records = RecordStore(context)
    private val modes = ModeStore(context)
    private var engine = Engine()
    /** 내비 경로 시작·끝을 듣는다. 경로가 시작되면 실제 신호 코치를 켜고 경로를 붙인다. */
    private val routes = RouteWatcher(context, onStarted = ::onRouteStarted, onEnded = ::onRouteEnded).apply { start() }
    private val appContext = context.applicationContext
    /** 경로 데모 동안 기어·속도·깜빡이를 에뮬레이터 Virtual Console에 보내 차량 화면도 같이 움직인다. */
    private val console = VehicleConsole()

    private val listeners = mutableSetOf<Listener>()
    private val handler = Handler(Looper.getMainLooper())
    private val hideOkPopup = Runnable {
        if (popup?.tone == PopupTone.OK) {
            popup = null
            notifyChanged(null)
        }
    }

    private val hidePreviewPopup = Runnable {
        if (running == null && popup != null) {
            popup = null
            notifyChanged(null)
        }
    }

    /** 앱·서비스를 다시 켜도 오늘 판정은 남는다. 날짜가 바뀌었으면 빈 상태로 시작한다. */
    var state = restoredToday(records.load())
        private set

    /** 경로 데모에서 마지막 프레임이 빨리 감는 구간이면 true. 지도 속도 표시에 "빨리 감기"를 붙인다. */
    var routeFastForward = false
        private set

    /** 마지막 프레임. 화면이 새로 붙을 때 신호 칸과 기어를 맞추는 데 쓴다. */
    var lastFrame: SignalFrame? = null
        private set

    /** 내비 위에 띄울 팝업. null이면 숨김. 규칙은 [nextPopup]. */
    var popup: Popup? = null
        private set

    /** 우리 앱 화면이 지금 보이는지. 보이면 팝업 대신 앱 화면의 코치 카드를 쓴다. */
    var appVisible = false
        set(value) {
            field = value
            notifyChanged(null)
        }

    /** 앱 칸의 크기. 앱 화면이 보일 때 잰다. 팝업을 이 칸(내비 자리) 가운데에 띄운다. */
    var appArea: Rect? = null

    /** 지금 도는 것: 시나리오 파일 이름, 실제 주행이면 [LIVE], 멈춰 있으면 null. */
    var running: String? = null
        private set

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun play(fileName: String) {
        stopAll()
        startDrive(fileName.substringBefore('_'), fileName)
        player.play(fileName, onFrame = ::onFrame, onEnd = {
            running = null
            notifyChanged(null)
        })
    }

    /**
     * PT용 경로 데모. 목적지 검색에서 고른 목적지의 미리 계산한 경로를 따라 달리는 프레임을 재생한다 ([RouteDemo]).
     * 출발 전 기어 P에서 경로 미리보기, 코치 지점마다 안내·판정, 도착하면 경로 리포트.
     * 차선 변경·급정거·커브 같은 영상용 안내는 엔진 룰이 없어서 각본 시각([RouteDemo.ScriptCue])에 팝업과 음성만 낸다.
     */
    fun playRouteDemo(destination: DemoDestination) {
        stopAll()
        val run = RouteDemo.build(RouteDemo.parse(player.readAsset(destination.asset)))
        startDrive(ROUTE_DEMO, ROUTE_DEMO)
        attachRoute(run.plan.copy(destination = destination.name))
        console.start()
        // 출발 전: 기어 P 프레임 하나로 미리보기를 띄우고 "출발"을 기다린다 ([departRouteDemo]).
        val parked = run.frames.first()
        console.mirror(parked)
        departRun = run
        onFrame(parked)
    }

    /** 출발을 기다리는 경로 데모. "출발"을 누르면 [departRouteDemo]가 재생한다. */
    private var departRun: RouteDemo.Run? = null

    /** 경로 데모가 출발 전 미리보기에서 "출발"을 기다리는 중이면 true. */
    val waitingDepart: Boolean get() = departRun != null

    /** 경로 미리보기의 "출발": 기어 D로 출발해 경로를 따라 달린다. 도착하면 기어 P와 경로 리포트. */
    fun departRouteDemo() {
        val run = departRun ?: return
        departRun = null
        player.playFrames(
            run.frames.filter { it.t >= RouteDemo.PREVIEW_MS },
            onFrame = { frame ->
                console.mirror(frame)
                routeFastForward = frame.t in run.fastForward
                // 도착 프레임에서 경로를 끝낸다. 뒤따르는 기어 P 프레임에서 경로 리포트가 열린다.
                if (frame.t == run.arriveAt) state = state.endRoute(RouteStatus.ARRIVED)
                scriptSpeech(run.scriptCues, frame.t)?.let(speaker::speak)
                onFrame(frame, scriptPopup(run.scriptCues, frame.t))
            },
            onEnd = {
                console.stop()
                running = null
                routeFastForward = false
                notifyChanged(null)
            },
        )
    }

    /**
     * 내비 경로가 시작됐다 (전이표는 [routeStartAction]).
     * 코치가 꺼져 있으면 아무것도 하지 않는다. 멈춰 있었으면 실제 신호 코치를 켠다. 위치·속도 권한이 없으면 켜지 않는다 (권한은 화면에서만 물을 수 있다).
     */
    private fun onRouteStarted(plan: RoutePlan) {
        when (routeStartAction(running, coachOn)) {
            RouteStartAction.START_LIVE -> {
                if (!hasLivePermissions()) {
                    Log.w(TAG, "경로가 시작됐지만 위치·속도 권한이 없어 코치를 켜지 않음")
                    return
                }
                startLive()
            }
            RouteStartAction.ATTACH -> Unit
            RouteStartAction.IGNORE -> return
        }
        attachRoute(plan)
    }

    /** 내비 경로가 끝났다. 시나리오·경로 데모 재생 중이면 실제 내비 이벤트로 끝내지 않는다. */
    private fun onRouteEnded(status: RouteStatus) {
        if (running != LIVE && running != null) return
        state = state.endRoute(status)
        notifyChanged(null)
    }

    private fun attachRoute(plan: RoutePlan) {
        state = state.startRoute(plan)
        speaker.speak(state.message)
        notifyChanged(null)
    }

    private fun hasLivePermissions(): Boolean =
        LIVE_PERMISSIONS.all { appContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** 실제 차량 신호로 주행을 시작한다. 권한 확인은 화면 쪽에서 먼저 한다. */
    fun startLive() {
        stopAll()
        startDrive(LIVE, LIVE)
        collector.start(::onFrame)
    }

    /** 재생, 실제 주행, 음성을 모두 멈춘다. 오늘 기록은 남긴다. */
    fun stopAll() {
        player.stop()
        if (running == ROUTE_DEMO) console.stop()
        if (running == LIVE) collector.stop()
        speaker.stop()
        val wasRunning = running != null
        running = null
        popup = null
        routeFastForward = false
        departRun = null
        handler.removeCallbacks(hideOkPopup)
        handler.removeCallbacks(hidePreviewPopup)
        if (wasRunning) notifyChanged(null)
    }

    /** 데모: 안내 종류를 예시 값으로 보여주고 어떻게 하는지 읽는다. 재생은 멈춘다. 내비가 앞에 있으면 팝업으로 뜬다. */
    fun preview(kind: GuideKind) {
        stopAll()
        state = state.preview(kind)
        popup = state.previewPopup()
        // 미리보기에는 프레임이 오지 않아 팝업이 저절로 숨지 않는다. 시간이 지나면 숨긴다.
        handler.removeCallbacks(hidePreviewPopup)
        handler.postDelayed(hidePreviewPopup, PREVIEW_POPUP_MS)
        // 어떻게 하는지(레버 동작)를 음성으로도 읽는다. 실제 주행에서는 엔진 GUIDE 문구를 읽는다.
        speaker.speak(state.message)
        notifyChanged(null)
    }

    fun release() {
        stopAll()
        routes.release()
        handler.removeCallbacksAndMessages(null)
        speaker.release()
        listeners.clear()
    }

    private fun startDrive(label: String, key: String) {
        // 켜 둔 채로 자정을 넘겼으면 어제 기록을 비우고 새 날로 시작한다.
        if (records.dayChanged()) {
            state = CoachState()
            records.save(emptyList())
        }
        Log.i(TAG, "주행 시작 코치: ${modes.load()}")
        engine = Engine()
        state = state.resetDrive(label)
        popup = null
        running = key
        notifyChanged(null)
    }

    /** 운전 코치가 켜져 있는가 (코치 화면의 켬/끔). 경로 코치 등 다른 곳에서도 이 값을 읽는다. */
    val coachOn: Boolean get() = modes.isCoachOn()

    /** [demoPopup]: 경로 데모 각본 팝업(차선 변경 등). 있으면 엔진 출력으로 정한 팝업보다 먼저 보인다. */
    private fun onFrame(frame: SignalFrame, demoPopup: Popup? = null) {
        // 코치 끔은 '알림 끔'이다. 엔진은 계속 돌려 판정(KEPT/MISSED)은 기록하되,
        // 끄면 음성·카드·팝업 같은 안내만 내보내지 않는다(카드는 대기 상태로 둔다). 기획서 진단→교정→졸업의 기록이 끊기지 않게 하려는 것이다.
        // 주행 중에 바꿔도 다음 프레임부터 반영된다.
        val outputs = engine.onFrame(frame)
        outputs.forEach { Log.i(TAG, "${frame.t} $it") }
        val shown = if (guidesOn(modes.load())) outputs else outputs.filterNot { it is Guide }
        shown.filterIsInstance<Guide>().forEach { speaker.speak(it.text) }
        val eventsBefore = state.todayEvents.size
        state = state.next(frame, shown, quiet = !coachOn)
        // 판정이 새로 생겼을 때만 저장한다. 매 프레임 쓰지 않는다.
        if (state.todayEvents.size != eventsBefore) records.save(state.todayEvents)
        if (coachOn) updatePopup(shown, frame) else popup = null
        if (coachOn && demoPopup != null) popup = demoPopup
        lastFrame = frame
        notifyChanged(frame)
    }

    /** 파랑("좋아요")으로 바뀌면 [OK_POPUP_MS] 뒤에 숨긴다. 그 사이 주황으로 돌아가면 숨기지 않는다. */
    private fun updatePopup(outputs: List<EngineOutput>, frame: SignalFrame) {
        val before = popup
        popup = nextPopup(before, state, outputs, frame)
        if (popup?.tone == PopupTone.OK && before?.tone != PopupTone.OK) {
            handler.removeCallbacks(hideOkPopup)
            handler.postDelayed(hideOkPopup, OK_POPUP_MS)
        }
    }

    private fun notifyChanged(frame: SignalFrame?) {
        listeners.toList().forEach { it.onSessionChanged(frame) }
    }

    companion object {
        const val LIVE = "실차"
        /** 경로 데모 재생 중일 때 [running] 값. 기록 앞에도 붙는다. */
        const val ROUTE_DEMO = "경로"
        /** 실제 차량 신호에 필요한 런타임 권한. 화면이 묻고, 경로 자동 시작은 있는지만 본다. */
        val LIVE_PERMISSIONS = listOf("android.car.permission.CAR_SPEED", "pleos.car.permission.FUSED_LOCATION")
        /** 파랑 "좋아요" 팝업을 보여주는 시간. 영상에서 읽을 수 있게 4초. */
        private const val OK_POPUP_MS = 4000L
        private const val PREVIEW_POPUP_MS = 6000L
        private const val TAG = "KkamCoach"
    }
}
