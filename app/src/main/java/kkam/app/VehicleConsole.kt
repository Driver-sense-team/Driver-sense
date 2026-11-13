package kkam.app

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kkam.engine.model.Gear
import kkam.engine.model.SignalFrame
import kkam.engine.model.TurnSignal
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Pleos 에뮬레이터 Virtual Console(에뮬레이터 안 127.0.0.1:8088)에 경로 데모의 기어(필요하면 속도·깜빡이)를 보낸다.
 * 그러면 차량 화면 계기판과 Vehicle SDK 값도 데모와 같이 움직인다. 실제 차량에는 콘솔이 없어서 실패해도 무시한다.
 *
 * 콘솔은 브라우저 세션이 살아 있을 때만 값을 받는다 ("emulator_inactive" 오류).
 * 그래서 콘솔 웹 페이지처럼 1초마다 세션 하트비트(POST /browser/session)를 보낸다 (콘솔의 세션 유지 시간은 3초).
 * 네트워크는 메인 스레드에서 못 쓰므로 전용 스레드 하나에서 순서대로 보낸다.
 */
class VehicleConsole(private val baseUrl: String = DEFAULT_URL) {
    /**
     * 속도는 보내지 않는다 (늘 0, 브레이크). 차가 움직이면(속도 > 0) 차량 OS가 주행 중 제한으로 우리 앱 화면을 막는다
     * ("주행 중에는 사용할 수 없습니다"). 경로 데모의 속도는 앱 지도 위에 표시한다.
     */
    private val sendSpeed = false
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var last: SignalFrame? = null

    private val heartbeat = object : Runnable {
        override fun run() {
            send("POST", "/browser/session?client=$CLIENT")
            handler?.postDelayed(this, HEARTBEAT_MS)
        }
    }

    fun start() {
        if (thread != null) return
        val started = HandlerThread("vehicle-console").also { it.start() }
        thread = started
        handler = Handler(started.looper).also { it.post(heartbeat) }
        last = null
    }

    /** 프레임 하나를 콘솔에 반영한다. 바뀐 값만 보낸다 ([consoleWrites]). */
    fun mirror(frame: SignalFrame) {
        val writes = consoleWrites(last, frame, sendSpeed)
        last = frame
        val h = handler ?: return
        writes.forEach { path -> h.post { send("PUT", path) } }
    }

    /** 차를 세워 두고(깜빡이 끔, 속도 0, 기어 P) 세션을 닫는다. */
    fun stop() {
        val h = handler ?: return
        h.removeCallbacks(heartbeat)
        h.post {
            send("POST", "/browser/session?client=$CLIENT") // 마지막 쓰기 전에 세션을 살려 둔다
            PARK_WRITES.forEach { send("PUT", it) }
            send("POST", "/browser/session?client=$CLIENT&closing=1")
        }
        thread?.quitSafely()
        thread = null
        handler = null
        last = null
    }

    private fun send(method: String, path: String) {
        try {
            val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val code = connection.responseCode
            if (code !in HTTP_OK_RANGE) Log.w(TAG, "$method $path → $code")
            connection.disconnect()
        } catch (e: IOException) {
            Log.w(TAG, "$method $path 실패 ${e.message}")
        }
    }

    companion object {
        const val DEFAULT_URL = "http://127.0.0.1:8088"
        private const val CLIENT = "kkam-route-demo"
        private const val HEARTBEAT_MS = 1000L
        private const val TIMEOUT_MS = 1500
        private val HTTP_OK_RANGE = 200..299
        private const val TAG = "KkamConsole"

        /** 데모가 끝나면 보내는 값: 깜빡이 끔, 속도 0, 기어 P. */
        val PARK_WRITES = listOf(
            "/vehicle/turn-signal?mode=Off",
            "/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0",
            "/vehicle/gear?gear=Park",
        )
    }
}

/**
 * 이전 프레임과 비교해 콘솔에 보낼 경로 목록. 기어·깜빡이는 바뀔 때만, 속도는 정수 km/h가 바뀔 때만 보낸다.
 * [sendSpeed]가 false면 속도는 늘 0으로 보낸다 (차는 정지(IDLING)로 남아 앱 화면이 막히지 않는다).
 * 콘솔 API는 Virtual Console 웹 페이지가 쓰는 것과 같다 (PUT, 값은 쿼리 문자열).
 */
fun consoleWrites(
    previous: SignalFrame?,
    frame: SignalFrame,
    sendSpeed: Boolean = true,
    sendTurnSignal: Boolean = false,
): List<String> {
    val writes = mutableListOf<String>()
    if (previous?.gear != frame.gear) writes += "/vehicle/gear?gear=${frame.gear.consoleName()}"

    // 속도를 안 보낼 때는 속도 0에 브레이크를 밟아 둔다. 떼면 차가 서서히 움직이는(크리핑) 것으로 보고 주행 중 제한이 걸린다.
    fun kphOf(f: SignalFrame) = if (sendSpeed) f.speedKph.roundToInt() else 0
    fun brakingOf(f: SignalFrame) = f.brake || !sendSpeed
    val kph = kphOf(frame)
    val braking = brakingOf(frame)
    if (previous == null || kphOf(previous) != kph || brakingOf(previous) != braking) {
        val accelerator = if (kph > 0 && !braking) ACCELERATOR_PCT else 0
        val brake = if (braking) BRAKE_PCT else 0
        writes += "/vehicle/controls?speed=$kph&accelerator=$accelerator&brake=$brake&steering=0"
    }

    // 깜빡이는 기본으로 보내지 않는다. 차량에 깜빡이를 켜면 사각지대 카메라 화면이 떠서 앱 지도를 가린다.
    // 코치 카드와 엔진 판정은 데모 프레임의 깜빡이 값으로 돌아서 영향이 없다.
    if (sendTurnSignal && previous?.turnSignal != frame.turnSignal) {
        writes += "/vehicle/turn-signal?mode=${frame.turnSignal.consoleName()}"
    }
    return writes
}

private fun Gear.consoleName(): String = when (this) {
    Gear.P -> "Park"
    Gear.R -> "Reverse"
    Gear.N -> "Neutral"
    Gear.D -> "Drive"
}

private fun TurnSignal.consoleName(): String = when (this) {
    TurnSignal.OFF -> "Off"
    TurnSignal.LEFT -> "BlinkLeft"
    TurnSignal.RIGHT -> "BlinkRight"
    TurnSignal.HAZARD -> "Off" // 비상등은 콘솔에서 /vehicle/lights로 따로 켠다. 데모에서는 쓰지 않는다.
}

/** 계기판 페달 그림용 값. 속도는 speed로 직접 정하고, 페달은 보이기만 한다. */
private const val ACCELERATOR_PCT = 20
private const val BRAKE_PCT = 30
