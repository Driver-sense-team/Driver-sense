package kkam.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import kkam.engine.model.Gear
import kkam.engine.model.SignalFrame
import kotlin.math.roundToInt

/**
 * 경로 데모의 앱 안 지도 (네이버 지도 SDK). 주행 화면일 때 앱 칸 전체(routeMapPanel)에 그린다.
 * 경로선, 목적지, 차 위치를 보여주고, 진행 방향이 화면 위로 오게 카메라를 돌린다 (내비처럼).
 * 주행 화면이라 터치로 지도를 움직이지 못하게 막는다 (기획서 5.4: 주행 중 터치 조작 없음).
 *
 * 키(NAVER_MAP_CLIENT_ID)는 local.properties에서 BuildConfig로 들어온다. 키가 없으면 지도 뷰를 만들지 않고
 * 속도와 안내 카드만 보인다. MapView는 화면 생명주기를 그대로 받아야 해서 MainActivity가 on* 함수를 부른다.
 */
class RouteMap(private val activity: Activity) {
    private val panel: FrameLayout = activity.findViewById(R.id.routeMapPanel)
    private val speed: TextView = activity.findViewById(R.id.routeMapSpeed)
    /** 지도 위쪽 가운데 안내 카드. 내비 위 팝업과 같은 모양. */
    private val popupCard = MapPopupCard(panel)
    /** 키가 없으면 null. 그때는 지도 없이 속도와 카드만 보인다. */
    private val mapView: MapView?
    /** 지도가 준비되면(getMapAsync) 채운다. 그 전에 온 그리기는 [pending]에 기억했다가 준비되면 그린다. */
    private var naverMap: NaverMap? = null
    private val line = PathOverlay()
    private val destination = Marker()
    /** 지금 그린 경로. 바뀌면 경로선과 목적지를 다시 놓는다. */
    private var drawnPath: List<LatLon>? = null
    /** 지도가 준비되기 전에 온 마지막 그리기. 준비되면 한 번 그린다. */
    private var pending: ((NaverMap) -> Unit)? = null

    init {
        val clientId = BuildConfig.NAVER_MAP_CLIENT_ID
        mapView = if (clientId.isEmpty()) {
            Log.w(TAG, "NAVER_MAP_CLIENT_ID 없음: 경로 데모 지도를 띄우지 않는다")
            null
        } else {
            NaverMapSdk.getInstance(activity).apply {
                // 새 NCP Maps 콘솔에서 발급한 키는 NcpKeyClient로 인증한다 (예전 NaverCloudPlatformClient 아님).
                client = NaverMapSdk.NcpKeyClient(clientId)
                setOnAuthFailedListener { e -> Log.e(TAG, "네이버 지도 인증 실패: ${e.errorCode} ${e.message}") }
            }
            MapView(activity)
        }
        mapView?.let { view ->
            view.onCreate(null)
            panel.addView(view, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            view.getMapAsync(::onMapReady)
        }
        line.apply {
            color = activity.getColor(R.color.ok)
            outlineWidth = 0
            width = activity.dp(LINE_WIDTH_DP)
        }
        destination.icon = OverlayImage.fromBitmap(bitmapOf(R.drawable.ic_kk_destination))
    }

    private fun onMapReady(map: NaverMap) {
        naverMap = map
        map.uiSettings.apply {
            // 주행 화면에서는 지도를 손으로 움직이지 않는다
            setAllGesturesEnabled(false)
            isZoomControlEnabled = false
            isScaleBarEnabled = false
            isCompassEnabled = false
            // 네이버 로고는 가리면 안 된다. 왼쪽 아래 속도 표시와 겹치지 않게 오른쪽 아래로 옮긴다.
            logoGravity = Gravity.BOTTOM or Gravity.END
        }
        map.locationOverlay.apply {
            icon = OverlayImage.fromBitmap(bitmapOf(R.drawable.ic_kk_car_arrow))
            // 기본 원(정확도 표시)은 데모 차에는 필요 없다
            circleRadius = 0
        }
        pending?.invoke(map)
    }

    /** 경로 좌표가 있고 주행 화면일 때만 보인다. */
    fun show(visible: Boolean) {
        panel.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * 경로선과 차 위치, 안내 카드를 그린다.
     * 주행 중(기어 P가 아님)이면 차가 가운데, 진행 방향이 위. 출발 전 미리보기(기어 P)면 경로 전체가 보이게 북쪽 위로 맞춘다.
     * [fastForward]면 속도 옆에 "빨리 감기"를 붙인다 (경로 데모의 시간 압축 구간).
     */
    fun render(path: List<LatLon>, frame: SignalFrame?, popup: Popup?, fastForward: Boolean = false) {
        popupCard.show(popup)
        speed.visibility = if (frame != null) View.VISIBLE else View.GONE
        if (frame != null) {
            val kph = frame.speedKph.roundToInt()
            speed.text = activity.getString(if (fastForward) R.string.route_map_speed_fast else R.string.route_map_speed, kph)
        }
        pending = { map -> draw(map, path, frame) }
        naverMap?.let { draw(it, path, frame) }
    }

    /** 목적지 검색 화면: 경로 없이 출발지(현재 위치)에 차를 두고 그 둘레를 보여준다. */
    fun focus(start: LatLon) {
        popupCard.show(null)
        speed.visibility = View.GONE
        pending = { map -> drawStart(map, start) }
        naverMap?.let { drawStart(it, start) }
    }

    private fun draw(map: NaverMap, path: List<LatLon>, frame: SignalFrame?) {
        drawPath(map, path)
        if (frame == null) return
        val here = LatLng(frame.lat, frame.lon)
        map.locationOverlay.apply {
            position = here
            bearing = frame.headingDeg.toFloat()
            isVisible = true
        }
        if (frame.gear == Gear.P && path.size >= 2 && frame.speedKph == 0.0 && frame.tbt != null) {
            // 출발 전 미리보기: 경로 전체
            map.moveCamera(CameraUpdate.fitBounds(LatLngBounds.from(path.map { LatLng(it.lat, it.lon) }), activity.dp(OVERVIEW_PADDING_DP)))
        } else {
            // bearing을 진행 방향으로 두면 진행 방향이 화면 위가 된다. 기울기는 0 (위에서 내려다봄).
            map.cameraPosition = CameraPosition(here, ZOOM, 0.0, frame.headingDeg)
        }
    }

    private fun drawStart(map: NaverMap, start: LatLon) {
        drawPath(map, emptyList())
        val here = LatLng(start.lat, start.lon)
        map.locationOverlay.apply {
            position = here
            bearing = 0f
            isVisible = true
        }
        map.cameraPosition = CameraPosition(here, SEARCH_ZOOM)
    }

    /** 경로가 바뀌었을 때만 경로선과 목적지를 다시 놓는다. 빈 경로면 지운다. */
    private fun drawPath(map: NaverMap, path: List<LatLon>) {
        if (path == drawnPath) return
        drawnPath = path
        // PathOverlay는 좌표가 2개 이상이어야 한다
        if (path.size >= 2) {
            line.coords = path.map { LatLng(it.lat, it.lon) }
            line.map = map
        } else {
            line.map = null
        }
        val end = path.lastOrNull()
        if (end != null) {
            destination.position = LatLng(end.lat, end.lon)
            destination.map = map
        } else {
            destination.map = null
        }
    }

    // MapView 생명주기. MainActivity의 같은 이름 함수에서 부른다.
    fun onStart() = mapView?.onStart()
    fun onResume() = mapView?.onResume()
    fun onPause() = mapView?.onPause()
    fun onStop() = mapView?.onStop()
    fun onSaveInstanceState(outState: Bundle) = mapView?.onSaveInstanceState(outState)
    fun onLowMemory() = mapView?.onLowMemory()
    fun onDestroy() = mapView?.onDestroy()

    /** 벡터 drawable을 지도 아이콘(OverlayImage)용 비트맵으로 바꾼다. */
    private fun bitmapOf(resId: Int): Bitmap {
        val drawable = requireNotNull(activity.getDrawable(resId))
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private companion object {
        const val TAG = "RouteMap"
        const val ZOOM = 17.0
        /** 목적지 검색 화면에서 출발지 둘레를 보는 확대. */
        const val SEARCH_ZOOM = 15.0
        /** 경로 미리보기에서 경로 전체를 맞출 때 가장자리 여백. */
        const val OVERVIEW_PADDING_DP = 80
        const val LINE_WIDTH_DP = 10
    }
}
