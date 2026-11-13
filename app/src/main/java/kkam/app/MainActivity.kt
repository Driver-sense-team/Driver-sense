package kkam.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import kkam.engine.model.Gear
import kkam.engine.model.HeadlightState
import kkam.engine.model.WiperMode
import kkam.engine.model.LawEntry
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import kotlin.math.roundToInt

/**
 * 주행 화면(코치 카드)과 시연용 데모 패널을 띄운다.
 * 데모 패널(F9)은 debug 빌드에서만 열리고, 주행 화면 레이아웃과 따로 있다.
 * 재생, 신호 수집, 엔진, 음성은 [CoachService]의 [CoachSession]이 맡는다. 이 화면은 세션 상태를 그리기만 한다.
 * 그래서 내비가 앞에 와서 이 화면이 숨겨져도 안내는 계속된다.
 */
class MainActivity : Activity() {
    /** 서비스에 붙기 전에는 null. 붙으면 [onSessionChanged]로 상태를 받는다. */
    private var session: CoachSession? = null
    private val state: CoachState get() = session?.state ?: CoachState()
    private val sessionListener = CoachSession.Listener(::onSessionChanged)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val connected = (binder as CoachService.LocalBinder).session
            session = connected
            connected.addListener(sessionListener)
            connected.appVisible = started
            appArea?.let { connected.appArea = it }
            // 서비스가 먼저 돌고 있었으면 (내비를 보다가 돌아온 경우) 마지막 프레임으로 화면을 맞춘다.
            connected.lastFrame?.let { frame ->
                gear = frame.gear
                screen = nextScreen(screen, frame.gear, connected.state.route)
                showSignals(frame)
            }
            highlight(connected.running)
            render()
            // 릴리스 APK에는 데모 패널이 없어서 실제 주행을 시작할 버튼도 없다. 앱을 켜면 바로 실제 차량 신호를 받는다.
            if (!debuggable && connected.running == null) toggleLive()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            session = null
        }
    }
    /** onStart~onStop 사이. 세션에 알려서 이 화면이 보이는 동안은 내비 위 팝업을 숨긴다. */
    private var started = false
    /** 앱 칸의 화면 위치. 서비스에 붙기 전에 잴 수도 있어서 따로 들고 있다가 넘긴다. */
    private var appArea: Rect? = null

    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var permissionsAsked = false
    /** 안내 종류 미리보기에서 다음에 보여줄 종류. */
    private var previewIndex = 0

    /**
     * 데모: 시나리오를 틀면 내비를 앞으로 띄운다 (내비 위 팝업 시연용).
     * 내비와 앱 칸이 바뀔 때 시스템이 이 화면을 다시 만들기 때문에, 변수에만 두면 "끔"으로 돌아간다. 그래서 저장해 둔다.
     */
    private var naviAfterPlay: Boolean
        get() = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_NAVI_AFTER_PLAY, false)
        set(value) = getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_NAVI_AFTER_PLAY, value).apply()
    /** 지금 screenSlot에 보이는 화면. 마지막 프레임의 기어로 정한다. */
    private var screen = Screen.DRIVE
    /** 마지막 프레임의 기어. 아직 프레임이 없으면 null. 정차 화면은 P일 때만 연다. */
    private var gear: Gear? = null
    private lateinit var diagnoseScreen: DiagnoseScreen
    private lateinit var driveScreen: DriveScreen
    private lateinit var reportScreen: ReportScreen
    private lateinit var progressScreen: ProgressScreen
    private lateinit var qnaScreen: QnaScreen
    private lateinit var modeScreen: ModeScreen
    private lateinit var routePreviewScreen: RoutePreviewScreen
    private lateinit var routeMap: RouteMap
    private lateinit var routeReportScreen: RouteReportScreen
    private lateinit var destinationSearch: DestinationSearch
    /** 경로 데모 목적지 검색창을 보고 있으면 true. 재생이 시작되면 닫는다. */
    private var searching = false
    /** 경로 데모 출발지 (현재 위치로 보여준다). 경로 데모 파일의 start와 첫 좌표. */
    private val demoStart: Pair<String, LatLon> by lazy {
        val route = RouteDemo.parse(assets.open(RouteDemo.ASSET).bufferedReader().use { it.readText() })
        route.start to route.points.first()
    }
    /** 미리보기 화면을 이미 정한 경로. 같은 경로에서 미리보기를 다시 열지 않는다. */
    private var routeHandled: RouteTrip? = null
    private lateinit var parkedNav: ParkedNav
    /** 진단 값. TODO(AI 담당 습관 인사이트, 12/1): 실제 계산 구현으로 바꾼다. */
    private val diagnosisSource: DiagnosisSource = DemoDiagnosisSource
    private val laws: List<LawEntry> by lazy { loadLaws() }

    /** debug 빌드면 true. 데모 패널은 debug에서만 열고, 릴리스는 켜자마자 실제 주행을 시작한다. */
    private val debuggable by lazy { applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    private val tiles = mutableMapOf<String, TextView>()
    private val buttons = mutableMapOf<String, Button>()
    private val view by lazy { Views(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        TILE_LABELS.forEach(::addTile)
        setUpScreens()
        addButton(DIAGNOSE_LABEL, getString(R.string.demo_open_diagnose)) { openDiagnoseParked() }
        addPreviewButton()
        addLiveButton()
        addButton(CoachSession.ROUTE_DEMO, getString(R.string.demo_route)) { openDestinationSearch() }
        ScenarioPlayer(this).fileNames().forEach(::addScenarioButton)
        // 시나리오 재생용 토글이라 경로 데모 시연에서는 쓰지 않는다. 패널 맨 뒤에 둔다.
        addNaviButton()
        setUpDemoToggle()
        render()

        watchDrivingRestriction()
        askPermissionsWhenParked()

        CoachService.start(this)
        bindService(Intent(this, CoachService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    /** 법규 룰 테이블은 루트 data/law_table.json을 assets로 바로 쓴다 (app/build.gradle.kts). */
    private fun loadLaws(): List<LawEntry> =
        parseLawTable(assets.open(LAW_TABLE_FILE).bufferedReader().use { it.readText() })

    /**
     * 차량의 주행 중 제한(UX 제한)을 본다. 제한 중에는 권한 확인 화면이 막히고("주행 중에는 사용할 수 없습니다")
     * 운전 중에 묻는 것도 방해라서, 권한은 제한이 풀렸을 때(정차) 묻는다.
     * TODO(주행 제한): 같은 리스너로 제한 중에는 데모 패널을 닫고 demoToggle을 숨기며,
     *  screenSlot을 주행 화면에서 다른 화면으로 바꾸지 않는다 (onSessionChanged의 화면 결정).
     */
    private fun watchDrivingRestriction() {
        val connected = Car.createCar(this) ?: return
        car = connected
        uxManager = (connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager)?.also {
            it.registerListener { restrictions ->
                if (!restrictions.isRequiresDistractionOptimization) askPermissionsWhenParked()
            }
        }
    }

    private fun drivingRestricted(): Boolean =
        uxManager?.currentCarUxRestrictions?.isRequiresDistractionOptimization ?: false

    /** 권한은 앱을 켠 뒤 처음으로 정차 상태일 때 한 번 묻는다. */
    private fun askPermissionsWhenParked() {
        if (permissionsAsked || drivingRestricted()) return
        permissionsAsked = true
        askNotificationPermission()
        askOverlayPermission()
    }

    /** 상단 알림(겹침 창을 못 쓸 때)에 필요한 알림 권한. Android 13부터 런타임에 묻는다. */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFY)
    }

    /**
     * "다른 앱 위에 표시" 권한 안내. 차량에 그 설정 화면이 있을 때만, 한 번만 묻는다.
     * Pleos 에뮬레이터에는 설정 화면이 없어서 묻지 않고 상단 알림을 쓴다.
     */
    private fun askOverlayPermission() {
        if (Settings.canDrawOverlays(this)) return
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        if (intent.resolveActivity(packageManager) == null) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_OVERLAY_ASKED, false)) return
        prefs.edit().putBoolean(KEY_OVERLAY_ASKED, true).apply()
        AlertDialog.Builder(this)
            .setTitle(R.string.overlay_ask_title)
            .setMessage(R.string.overlay_ask_body)
            .setPositiveButton(R.string.overlay_ask_open) { _, _ -> startActivity(intent) }
            .setNegativeButton(R.string.overlay_ask_later, null)
            .show()
    }

    /** 데모 패널에 지금 쓰는 안내 방식을 보여준다. 권한은 설정에서 바뀔 수 있어서 화면이 보일 때마다 확인한다. */
    private fun showNotifierStatus() {
        view.demoNotifier.setText(
            if (Settings.canDrawOverlays(this)) R.string.demo_notifier_popup else R.string.demo_notifier_headsup,
        )
    }

    /** 데모 패널은 debug 빌드에서만 열 수 있다. 릴리스 APK에는 여는 버튼이 없다. */
    private fun setUpDemoToggle() {
        if (!debuggable) return
        view.demoToggle.visibility = View.VISIBLE
        view.demoToggle.background = pillBackground(getColor(R.color.surface), strokeColor = getColor(R.color.line))
        view.demoToggle.setOnClickListener { showDemoPanel(view.demoPanel.visibility != View.VISIBLE) }
    }

    private fun showDemoPanel(show: Boolean) {
        view.demoPanel.visibility = if (show) View.VISIBLE else View.GONE
        view.demoToggle.setText(if (show) R.string.demo_close else R.string.demo_open)
    }

    /**
     * screenSlot의 화면들과 기어 P 레일을 만든다. 화면마다 담당 클래스가 자기 레이아웃만 그린다.
     */
    private fun setUpScreens() {
        driveScreen = DriveScreen(this)
        reportScreen = ReportScreen(this) { laws }
        progressScreen = ProgressScreen(this)
        qnaScreen = QnaScreen(this, KeywordQnaAnswerer(laws))
        modeScreen = ModeScreen(this)
        routePreviewScreen = RoutePreviewScreen(this, diagnosis = { diagnosisSource.load() }, onDepart = { session?.departRouteDemo() })
        routeMap = RouteMap(this)
        routeReportScreen = RouteReportScreen(this, laws = { laws }, onAsk = ::askFromRouteReport)
        destinationSearch = DestinationSearch(this, onSelect = ::playRouteDemo)
        parkedNav = ParkedNav(this, onSelect = ::showParked)
        diagnoseScreen = DiagnoseScreen(this, diagnosisSource.load())
    }

    /** 데모 패널 버튼. 재생을 멈추고 기어 P로 세운 뒤 진단 리포트를 연다. */
    private fun openDiagnoseParked() {
        session?.stopAll()
        highlight(null)
        gear = Gear.P
        tiles.getValue("기어").text = Gear.P.name
        showParked(Screen.DIAGNOSE)
    }

    /** 기어 P가 아니면 열지 않고 주행 화면에 머문다 (기획서 5.4). */
    private fun showParked(target: Screen) {
        screen = openScreen(target, gear)
        render()
    }

    override fun onStart() {
        super.onStart()
        routeMap.onStart()
        showNotifierStatus()
        started = true
        session?.appVisible = true
        // 앱 칸의 크기를 재 둔다. 내비도 같은 칸에 뜨므로 팝업을 이 칸 가운데에 띄운다 (CoachPopup 참고).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val area = windowManager.currentWindowMetrics.bounds
            appArea = area
            session?.appArea = area
        }
    }

    override fun onResume() {
        super.onResume()
        routeMap.onResume()
    }

    override fun onPause() {
        routeMap.onPause()
        super.onPause()
    }

    override fun onStop() {
        started = false
        session?.appVisible = false
        routeMap.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        routeMap.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        routeMap.onLowMemory()
    }

    /** 화면만 닫는다. 주행 중이면 서비스가 계속 돌고, 멈춰 있으면 서비스도 끝난다. */
    override fun onDestroy() {
        session?.removeListener(sessionListener)
        unbindService(connection)
        uxManager?.unregisterListener()
        routeMap.onDestroy()
        car?.disconnect()
        session = null
        super.onDestroy()
    }

    private fun play(fileName: String) {
        val current = session ?: return
        screen = Screen.DRIVE
        current.play(fileName)
        if (naviAfterPlay) NaviLauncher.open(this)
    }

    /**
     * 데모 패널 "경로 데모": 재생을 멈추고 기어 P로 세운 뒤, 지도 위 목적지 검색창을 연다.
     * 에뮬레이터 내비는 지도도 경로 계산도 안 되므로 앱 지도를 내비로 쓴다.
     */
    private fun openDestinationSearch() {
        session?.stopAll()
        highlight(null)
        gear = Gear.P
        tiles.getValue("기어").text = Gear.P.name
        screen = Screen.DRIVE
        searching = true
        destinationSearch.open(demoStart.first)
        render()
    }

    /**
     * 검색에서 목적지를 골랐다: 경로 데모를 시작한다 (출발 전 미리보기 → 코치 지점마다 안내 → 도착 리포트).
     * 내비 화면을 띄우지 않는다. 내비가 앱 지도를 가리기 때문이다 ("재생하면 내비 띄우기"는 시나리오 재생에만 쓴다).
     */
    private fun playRouteDemo(destination: DemoDestination) {
        val current = session ?: return
        searching = false
        screen = Screen.DRIVE
        current.playRouteDemo(destination)
    }

    /** 경로 리포트 "AI에게 묻기": 법규 Q&A를 열고 적색 우회전 질문을 바로 묻는다. 기어 P에서만 열린다. */
    private fun askFromRouteReport() {
        screen = openScreen(Screen.QNA, gear)
        if (screen == Screen.QNA) qnaScreen.askNow(ROUTE_REPORT_QUESTION)
        render()
    }

    /** 실제 차량 신호로 주행을 시작한다. 위치와 속도는 런타임 권한이 필요하다. */
    private fun toggleLive() {
        val current = session ?: return
        if (current.running == CoachSession.LIVE) {
            current.stopAll()
            return
        }
        val missing = CoachSession.LIVE_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startLive() else requestPermissions(missing.toTypedArray(), REQUEST_LIVE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LIVE && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startLive()
        }
    }

    private fun startLive() {
        screen = Screen.DRIVE
        session?.startLive()
    }

    /** 세션 상태가 바뀌었을 때. 새 프레임이면 기어로 화면을 정하고 신호 칸을 갱신한다. */
    private fun onSessionChanged(frame: SignalFrame?) {
        if (frame != null) {
            // 기어 P면 리포트(다른 정차 화면을 보고 있으면 그대로), 그 밖의 기어면 주행 화면 (기획서 5.4).
            gear = frame.gear
            screen = nextScreen(screen, frame.gear, state.route)
            showSignals(frame)
            openRoutePreview(frame.gear)
        }
        highlight(session?.running)
        render()
    }

    /**
     * 경로가 새로 붙은 뒤 첫 프레임에서 한 번만: 기어 P(출발 전)면 경로 미리보기를 연다.
     * 경로를 붙인 순간의 기어는 앞 주행의 값일 수 있어서, 새 프레임의 기어로 정한다.
     */
    private fun openRoutePreview(frameGear: Gear) {
        val trip = state.route ?: return
        val handled = routeHandled
        if (handled != null && trip.plan == handled.plan && trip.firstEventIndex == handled.firstEventIndex) return
        screen = screenOnRouteStart(screen, frameGear) ?: return
        routeHandled = trip
    }

    private fun showSignals(frame: SignalFrame) {
        tiles.getValue("속도").text = "${frame.speedKph.roundToInt()} km/h"
        tiles.getValue("핸들 각도").text = frame.steeringDeg?.let { "${it.roundToInt()}°" } ?: "-"
        tiles.getValue("깜빡이").text = frame.turnSignal.label()
        tiles.getValue("기어").text = frame.gear.name
        tiles.getValue("내비 TBT").text = frame.tbt?.let { "${it.turn.label()} ${it.distanceM} m" } ?: "-"
        tiles.getValue("전방 신호").text = frame.light?.phase?.name ?: "-"
        tiles.getValue("전조등").text = frame.headlight?.label() ?: "-"
        tiles.getValue("성에·순환").text = listOfNotNull(
            frame.defroster?.let { if (it) "성에 켜짐" else "성에 꺼짐" },
            frame.recirc?.let { if (it) "내기" else "외기" },
        ).joinToString(" · ").ifEmpty { "-" }
        tiles.getValue("와이퍼·비").text = listOfNotNull(frame.wiper?.label(), frame.rain?.let { if (it) "비" else "맑음" }).joinToString(" · ").ifEmpty { "-" }
    }

    /** 지금 화면을 그린다. 값은 모두 [CoachState]에서 온다. */
    private fun render() {
        parkedNav.show(screen)
        driveScreen.show(screen)
        reportScreen.show(screen)
        diagnoseScreen.show(screen)
        progressScreen.show(screen)
        qnaScreen.show(screen)
        modeScreen.show(screen)
        routePreviewScreen.show(screen)
        routeReportScreen.show(screen)
        if (screen == Screen.ROUTE_REPORT) routeReportScreen.render(state)
        if (screen == Screen.REPORT) reportScreen.render(state)
        if (screen == Screen.ROUTE_PREVIEW) routePreviewScreen.render(state)
        routePreviewScreen.showDepart(session?.waitingDepart == true)
        // 경로 데모 지도. 목적지 검색: 지도 전체 + 검색창. 주행: 지도 전체 + 지도 위 팝업 카드.
        // 출발 전 경로 미리보기: 지도(경로 전체)와 미리보기 화면을 반씩.
        val path = state.route?.plan?.path.orEmpty()
        val current = session
        // 재생이 시작됐거나 다른 화면을 열었으면 검색창을 닫는다
        if (searching && (current?.running != null || screen != Screen.DRIVE)) {
            searching = false
            destinationSearch.close()
        }
        val routeShown = path.isNotEmpty() && current?.running == CoachSession.ROUTE_DEMO
        val mapFull = searching || (screen == Screen.DRIVE && path.isNotEmpty())
        val mapHalf = !mapFull && screen == Screen.ROUTE_PREVIEW && routeShown
        routeMap.show(mapFull || mapHalf)
        view.screenSlot.visibility = if (mapFull) View.GONE else View.VISIBLE
        when {
            searching -> routeMap.focus(demoStart.second)
            mapFull || mapHalf -> routeMap.render(path, current?.lastFrame, current?.popup, current?.routeFastForward == true)
        }
        driveScreen.render(state)
        view.logs.text = state.logs.takeLast(MAX_LOGS).reversed().joinToString("\n").ifEmpty { getString(R.string.demo_no_logs) }
    }

    private fun addTile(label: String) {
        val tile = layoutInflater.inflate(R.layout.signal_tile, view.tiles, false) as LinearLayout
        tile.layoutParams = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
        ).apply { width = 0; setMargins(dp(4), dp(4), dp(4), dp(4)) }
        tile.background = rounded(getColor(R.color.surface), radiusDp = 14)
        tile.findViewById<TextView>(R.id.tileLabel).text = label
        tiles[label] = tile.findViewById<TextView>(R.id.tileValue).also { it.text = "-" }
        view.tiles.addView(tile)
    }

    private fun addLiveButton() = addButton(CoachSession.LIVE, "실제 차량 신호") { toggleLive() }

    /** 데모 패널: 누를 때마다 다음 안내 종류를 예시 값으로 보여준다. 내비 띄우기를 켜 두면 내비 위 팝업으로 본다. */
    private fun addPreviewButton() {
        addButton(PREVIEW_LABEL, getString(R.string.demo_preview_start)) {
            val kind = GuideKind.entries[previewIndex % GuideKind.entries.size]
            previewIndex++
            buttons.getValue(PREVIEW_LABEL).text = getString(R.string.demo_preview_now, kind.label)
            screen = Screen.DRIVE
            searching = false
            destinationSearch.close()
            session?.preview(kind)
            if (naviAfterPlay) NaviLauncher.open(this)
        }
    }

    /** 데모 패널 토글: 켜면 시나리오를 틀 때 내비를 앞으로 띄운다. */
    private fun addNaviButton() {
        addButton(NAVI_LABEL, naviButtonText()) {
            naviAfterPlay = !naviAfterPlay
            buttons.getValue(NAVI_LABEL).text = naviButtonText()
        }
    }

    private fun naviButtonText() =
        getString(if (naviAfterPlay) R.string.demo_navi_after_play_on else R.string.demo_navi_after_play_off)

    private fun addScenarioButton(fileName: String) =
        addButton(fileName, fileName.removeSuffix(".json").replace('_', ' ')) { play(fileName) }

    private fun addButton(key: String, label: String, onClick: () -> Unit) {
        val button = layoutInflater.inflate(R.layout.scenario_button, view.scenarios, false) as Button
        button.text = label
        button.setOnClickListener { onClick() }
        buttons[key] = button
        view.scenarios.addView(button)
        highlight(null)
    }

    private fun highlight(playing: String?) {
        buttons.forEach { (name, button) ->
            val color = getColor(if (name == playing) R.color.demo_active else R.color.surface)
            button.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    private fun WiperMode.label(): String = when (this) {
        WiperMode.OFF -> "꺼짐"
        WiperMode.ON -> "켜짐"
        WiperMode.AUTO -> "자동"
    }

    private fun HeadlightState.label(): String = when (this) {
        HeadlightState.OFF -> "꺼짐"
        HeadlightState.ON -> "켜짐"
        HeadlightState.AUTO -> "자동"
    }

    private fun TurnSignal.label(): String = when (this) {
        TurnSignal.OFF -> "꺼짐"
        TurnSignal.LEFT -> "좌 켜짐"
        TurnSignal.RIGHT -> "우 켜짐"
        TurnSignal.HAZARD -> "비상등"
    }

    /** 레이아웃 뷰를 한곳에 모은다. */
    private class Views(activity: Activity) {
        val demoPanel: View = activity.findViewById(R.id.demoPanel)
        val demoToggle: TextView = activity.findViewById(R.id.demoToggle)
        val demoNotifier: TextView = activity.findViewById(R.id.demoNotifier)
        val scenarios: LinearLayout = activity.findViewById(R.id.scenarios)
        val tiles: GridLayout = activity.findViewById(R.id.tiles)
        val logs: TextView = activity.findViewById(R.id.logs)
        val screenSlot: View = activity.findViewById(R.id.screenSlot)
    }

    private companion object {
        const val LAW_TABLE_FILE = "law_table.json"
        const val MAX_LOGS = 12
        const val NAVI_LABEL = "내비"
        const val PREVIEW_LABEL = "미리보기"
        const val DIAGNOSE_LABEL = "진단"
        const val REQUEST_LIVE = 1
        const val REQUEST_NOTIFY = 2
        const val PREFS = "kkam"
        const val KEY_OVERLAY_ASKED = "overlay_asked"
        const val KEY_NAVI_AFTER_PLAY = "navi_after_play"
        val TILE_LABELS = listOf("속도", "핸들 각도", "깜빡이", "기어", "내비 TBT", "전방 신호", "전조등", "와이퍼·비", "성에·순환")
    }
}
