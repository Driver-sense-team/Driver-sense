package kkam.app

import kkam.engine.model.Gear

/** screenSlot에 들어가는 화면. */
enum class Screen {
    /** 주행 화면 (UI-1). 코치 카드. */
    DRIVE,

    /** 오늘 주행 리포트 (UI-2). */
    REPORT,

    /** 진단 리포트 (UI-3). */
    DIAGNOSE,

    /** 교정 진행·주간 코칭 (UI-4). */
    PROGRESS,

    /** 법규 Q&A (UI-5). */
    QNA,

    /** 운전 코치 켬/끔 (F3). 예전 4가지 모드를 켬/끔 하나로 줄였다. */
    MODE,

    /** 경로 미리보기. 내비 경로가 시작됐을 때 기어 P면 보여준다 (RouteCoach.kt). */
    ROUTE_PREVIEW,

    /** 경로 리포트. 경로가 끝나고(도착·안내 종료) 기어 P가 되면 오늘 리포트 대신 보여준다. */
    ROUTE_REPORT,
}

/** 기어 P에서만 여는 화면 (기획서 5.4: 리포트, 진단, Q&A는 기어 P에서만). */
val Screen.parkedOnly: Boolean get() = this != Screen.DRIVE

/**
 * 새 프레임의 기어로 다음 화면을 정한다.
 * - P가 아니면 무조건 주행 화면.
 * - P인데 이미 정차 화면(리포트, 진단, 교정 진행, Q&A, 모드)을 보고 있으면 그대로 둔다. 리포트로 덮지 않는다.
 * - P인데 주행 화면이면 오늘 주행 리포트.
 */
fun nextScreen(current: Screen, gear: Gear): Screen = when {
    gear != Gear.P -> Screen.DRIVE
    current.parkedOnly -> current
    else -> Screen.REPORT
}

/** 사용자가 화면을 열려고 할 때. 기어 P가 아니면 P 전용 화면은 열지 않고 주행 화면에 머문다. */
fun openScreen(target: Screen, gear: Gear?): Screen =
    if (target.parkedOnly && gear != Gear.P) Screen.DRIVE else target

/**
 * 내비 경로가 새로 시작됐을 때. 기어 P(출발 전)면 경로 미리보기, 주행 중이면 지금 화면 그대로.
 * 기어를 아직 모르면(실제 신호 첫 프레임 전) 정하지 않고 null을 돌려준다. 다음 프레임에서 다시 본다.
 */
fun screenOnRouteStart(current: Screen, gear: Gear?): Screen? = when (gear) {
    null -> null
    Gear.P -> Screen.ROUTE_PREVIEW
    else -> current
}

/**
 * 새 프레임에서 경로까지 보고 다음 화면을 정한다. [nextScreen]에 한 줄을 더한 것이다.
 * 주행 화면에서 기어 P가 됐고 경로가 끝나 있으면(도착·안내 종료) 오늘 리포트 대신 경로 리포트.
 */
fun nextScreen(current: Screen, gear: Gear, route: RouteTrip?): Screen {
    val next = nextScreen(current, gear)
    val routeEnded = route != null && route.status != RouteStatus.DRIVING
    return if (next == Screen.REPORT && current == Screen.DRIVE && routeEnded) Screen.ROUTE_REPORT else next
}
