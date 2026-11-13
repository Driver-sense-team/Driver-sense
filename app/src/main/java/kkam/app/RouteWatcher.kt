package kkam.app

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 내비 경로 안내의 시작과 끝을 듣는다 (NaviHelper 3.0.5).
 *
 * - 시작: onRouteStarted(목적지 이름, 거리). 앱을 켰을 때 이미 안내 중이면 getRouteStateInfo → isRouting으로 안다.
 * - 회전 목록: 시작 직후 getTBTInfo로 받은 TBT 목록. SDK에 경로 전체 회전 목록을 따로 주는 API는 없다.
 * - 끝: onDestinationArrived(도착), onRouteEnded·onRouteCancelled(안내 종료·취소).
 *
 * 차량 신호 수집([SignalCollector])과 따로 돈다. 실제 신호 코치가 꺼져 있어도 경로 시작을 들어야 하기 때문이다.
 * SDK 콜백은 다른 스레드에서 올 수 있어서 모두 메인 스레드로 넘긴다.
 */
class RouteWatcher(
    context: Context,
    private val onStarted: (RoutePlan) -> Unit,
    private val onEnded: (RouteStatus) -> Unit,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var navi: NaviHelper? = null

    /** 경로 시작을 알았지만 회전 목록을 아직 못 받은 상태. 회전 목록이 오면 [onStarted]를 부른다. */
    private var pendingName: String? = null
    private var pendingDistanceM = 0
    /** 지금 안내 중인 경로를 이미 알렸는가. 같은 경로를 두 번 시작하지 않는다. */
    private var routing = false

    private val giveUpWaiting = Runnable { pendingName?.let { finishStart(emptyList()) } }

    fun start() {
        val helper = NaviHelper(appContext)
        navi = helper
        helper.initialize(
            {
                main.post {
                    helper.addListener(listener)
                    // 앱을 켜기 전에 이미 안내 중이었을 수 있다.
                    helper.getRouteStateInfo()
                }
            },
            { error -> Log.w(TAG, "navi connect failed ${error.message}") },
        )
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        navi?.let {
            it.removeListener(listener)
            it.release()
        }
        navi = null
    }

    private fun beginStart(name: String?, distanceM: Int) {
        if (routing) return
        routing = true
        pendingName = name?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
        pendingDistanceM = distanceM
        navi?.getTBTInfo()
        main.removeCallbacks(giveUpWaiting)
        main.postDelayed(giveUpWaiting, TBT_WAIT_MS)
    }

    private fun finishStart(tbt: List<TBTInfo>) {
        val name = pendingName ?: return
        pendingName = null
        main.removeCallbacks(giveUpWaiting)
        val plan = RoutePlan(name, pendingDistanceM, tbt.map { it.type.toEngineTurnType() })
        Log.i(TAG, "경로 시작 $plan TBT=${tbt.map { "${it.type} ${it.distance.toInt()}m" }}")
        onStarted(plan)
    }

    private fun end(status: RouteStatus) {
        if (!routing) return
        routing = false
        pendingName = null
        main.removeCallbacks(giveUpWaiting)
        Log.i(TAG, "경로 끝 $status")
        onEnded(status)
    }

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) {
            main.post { beginStart(info.name, info.distance) }
        }

        override fun onRouteStateInfo(info: RouteStateInfo) {
            // 이미 안내 중이면 목적지 이름을 물어보고, 답이 오면 시작으로 친다.
            // 에뮬레이터 내비는 안내 중에도 isRouting이 false로 와서 naviMode도 같이 본다.
            val guiding = info.isRouting || info.naviMode in GUIDING_MODES
            Log.i(TAG, "경로 상태 isRouting=${info.isRouting} naviMode=${info.naviMode}")
            if (guiding) main.post { if (!routing) navi?.getDestinationInfo() }
        }

        override fun onDestinationInfo(info: DestinationInfo) {
            main.post { beginStart(info.name, info.remainDistance) }
        }

        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            main.post { if (pendingName != null) finishStart(tbtInfoList) }
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            main.post { end(RouteStatus.ARRIVED) }
        }

        override fun onRouteEnded() {
            main.post { end(RouteStatus.ENDED) }
        }

        override fun onRouteCancelled() {
            main.post { end(RouteStatus.ENDED) }
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error $errorCode")
        }
    }

    private companion object {
        const val TAG = "KkamRoute"
        const val DEFAULT_NAME = "목적지"
        /** 회전 목록을 기다리는 시간. 넘으면 회전 없이 시작한다. */
        const val TBT_WAIT_MS = 3000L
        /** 경로 안내 중인 내비 모드. 모의 주행(SIMULATION_GUIDING)도 경로 안내로 본다. */
        val GUIDING_MODES = setOf(NaviMode.ROUTE_GUIDING, NaviMode.SIMULATION_GUIDING)
    }
}
